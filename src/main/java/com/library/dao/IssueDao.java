package com.library.dao;

import com.library.model.BookIssue;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) for Book Issue and Return transactions.
 * Uses atomic JDBC transactions to guarantee inventory consistency.
 */
@Repository
public class IssueDao {

    private final DataSource dataSource;

    public IssueDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves book issue records, optionally filtered by status ('ALL', 'ISSUED', 'RETURNED', 'OVERDUE').
     */
    public List<BookIssue> getAllIssues(String statusFilter) throws SQLException {
        List<BookIssue> issues = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT bi.*, b.title AS book_title, b.isbn AS book_isbn, m.name AS member_name, m.member_code " +
            "FROM book_issues bi " +
            "JOIN books b ON bi.book_id = b.id " +
            "JOIN members m ON bi.member_id = m.id "
        );

        if ("ISSUED".equalsIgnoreCase(statusFilter)) {
            sql.append("WHERE bi.status = 'ISSUED' ");
        } else if ("RETURNED".equalsIgnoreCase(statusFilter)) {
            sql.append("WHERE bi.status = 'RETURNED' ");
        } else if ("OVERDUE".equalsIgnoreCase(statusFilter)) {
            sql.append("WHERE bi.status = 'ISSUED' AND bi.due_date < CURDATE() ");
        }
        sql.append("ORDER BY bi.id DESC");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {

            LocalDate today = LocalDate.now();
            while (rs.next()) {
                BookIssue issue = new BookIssue();
                issue.setId(rs.getInt("id"));
                issue.setBookId(rs.getInt("book_id"));
                issue.setMemberId(rs.getInt("member_id"));
                issue.setIssueDate(rs.getString("issue_date"));
                issue.setDueDate(rs.getString("due_date"));
                issue.setReturnDate(rs.getString("return_date"));
                issue.setFineAmount(rs.getBigDecimal("fine_amount"));
                issue.setStatus(rs.getString("status"));
                issue.setNotes(rs.getString("notes"));

                // Joined fields
                issue.setBookTitle(rs.getString("book_title"));
                issue.setBookIsbn(rs.getString("book_isbn"));
                issue.setMemberName(rs.getString("member_name"));
                issue.setMemberCode(rs.getString("member_code"));

                // Real-time overdue calculation
                if ("ISSUED".equalsIgnoreCase(issue.getStatus()) && issue.getDueDate() != null) {
                    LocalDate dueDate = LocalDate.parse(issue.getDueDate());
                    if (today.isAfter(dueDate)) {
                        long days = ChronoUnit.DAYS.between(dueDate, today);
                        issue.setDaysOverdue(days);
                    } else {
                        issue.setDaysOverdue(0);
                    }
                } else {
                    issue.setDaysOverdue(0);
                }

                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * Issues a book to a member.
     * Wrapped in a JDBC transaction to ensure inventory count decrements reliably.
     */
    public boolean issueBook(int bookId, int memberId, int daysDue, String notes) throws SQLException {
        if (daysDue <= 0) {
            daysDue = 14;
        }

        String checkStockSql = "SELECT available_copies FROM books WHERE id = ? FOR UPDATE";
        String insertIssueSql = "INSERT INTO book_issues (book_id, member_id, issue_date, due_date, status, notes) " +
                                "VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL ? DAY), 'ISSUED', ?)";
        String decrementStockSql = "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?";

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Check book stock
            int availableCopies = 0;
            try (PreparedStatement checkPs = conn.prepareStatement(checkStockSql)) {
                checkPs.setInt(1, bookId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Book with ID " + bookId + " does not exist.");
                    }
                    availableCopies = rs.getInt("available_copies");
                }
            }

            if (availableCopies <= 0) {
                conn.rollback();
                throw new IllegalStateException("Book is currently OUT OF STOCK and cannot be issued.");
            }

            // 2. Insert into book_issues
            try (PreparedStatement insertPs = conn.prepareStatement(insertIssueSql)) {
                insertPs.setInt(1, bookId);
                insertPs.setInt(2, memberId);
                insertPs.setInt(3, daysDue);
                insertPs.setString(4, notes != null ? notes : "");
                insertPs.executeUpdate();
            }

            // 3. Decrement available copies
            try (PreparedStatement updatePs = conn.prepareStatement(decrementStockSql)) {
                updatePs.setInt(1, bookId);
                updatePs.executeUpdate();
            }

            conn.commit(); // Commit transaction
            return true;
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new SQLException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Processes book return, calculates any overdue fine ($2.00 per late day by default),
     * and restocks the book copy atomically.
     */
    public Map<String, Object> returnBook(int issueId, Double overrideFine) throws SQLException {
        String findIssueSql = "SELECT bi.*, b.id AS b_id FROM book_issues bi JOIN books b ON bi.book_id = b.id WHERE bi.id = ? FOR UPDATE";
        String updateIssueSql = "UPDATE book_issues SET return_date = CURDATE(), fine_amount = ?, status = 'RETURNED' WHERE id = ?";
        String incrementStockSql = "UPDATE books SET available_copies = available_copies + 1 WHERE id = ?";

        Connection conn = null;
        Map<String, Object> result = new HashMap<>();

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            int bookId = 0;
            String dueDateStr = null;
            String currentStatus = null;

            // 1. Fetch issue details
            try (PreparedStatement findPs = conn.prepareStatement(findIssueSql)) {
                findPs.setInt(1, issueId);
                try (ResultSet rs = findPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Issue record with ID " + issueId + " not found.");
                    }
                    bookId = rs.getInt("b_id");
                    dueDateStr = rs.getString("due_date");
                    currentStatus = rs.getString("status");
                }
            }

            if ("RETURNED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("This book has already been returned.");
            }

            // 2. Compute fine
            LocalDate dueDate = LocalDate.parse(dueDateStr);
            LocalDate today = LocalDate.now();
            long daysLate = 0;
            BigDecimal fine;

            if (today.isAfter(dueDate)) {
                daysLate = ChronoUnit.DAYS.between(dueDate, today);
            }

            if (overrideFine != null && overrideFine >= 0) {
                fine = BigDecimal.valueOf(overrideFine);
            } else {
                fine = BigDecimal.valueOf(daysLate * 2.0);
            }

            // 3. Update issue record
            try (PreparedStatement updatePs = conn.prepareStatement(updateIssueSql)) {
                updatePs.setBigDecimal(1, fine);
                updatePs.setInt(2, issueId);
                updatePs.executeUpdate();
            }

            // 4. Increment available copies
            try (PreparedStatement incPs = conn.prepareStatement(incrementStockSql)) {
                incPs.setInt(1, bookId);
                incPs.executeUpdate();
            }

            conn.commit();

            result.put("success", true);
            result.put("daysLate", daysLate);
            result.put("fineAmount", fine);
            result.put("returnDate", today.toString());
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new SQLException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Fetches dashboard metric counts.
     */
    public Map<String, Object> getDashboardStats() throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        String booksSql = "SELECT COALESCE(SUM(total_copies), 0) AS total_copies, COALESCE(SUM(available_copies), 0) AS available_copies, COUNT(*) AS unique_titles FROM books";
        String membersSql = "SELECT COUNT(*) AS total_members FROM members WHERE status = 'ACTIVE'";
        String issuesSql = "SELECT COUNT(*) AS active_issues, SUM(CASE WHEN due_date < CURDATE() THEN 1 ELSE 0 END) AS overdue_issues FROM book_issues WHERE status = 'ISSUED'";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(booksSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalCopies", rs.getInt("total_copies"));
                    stats.put("availableCopies", rs.getInt("available_copies"));
                    stats.put("uniqueTitles", rs.getInt("unique_titles"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(membersSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalMembers", rs.getInt("total_members"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(issuesSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("activeIssues", rs.getInt("active_issues"));
                    stats.put("overdueIssues", rs.getInt("overdue_issues"));
                }
            }
        }
        return stats;
    }
}
