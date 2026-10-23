package com.library.controller;

import com.library.dao.IssueDao;
import com.library.model.BookIssue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Book Issue and Return operations.
 */
@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "*")
public class IssueController {

    private final IssueDao issueDao;

    public IssueController(IssueDao issueDao) {
        this.issueDao = issueDao;
    }

    @GetMapping
    public ResponseEntity<List<BookIssue>> getIssues(@RequestParam(value = "status", defaultValue = "ALL") String status) throws SQLException {
        return ResponseEntity.ok(issueDao.getAllIssues(status));
    }

    @PostMapping("/borrow")
    public ResponseEntity<?> issueBook(@RequestBody Map<String, Object> payload) {
        if (!payload.containsKey("bookId") || !payload.containsKey("memberId")) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "bookId and memberId are required."));
        }

        try {
            int bookId = Integer.parseInt(payload.get("bookId").toString());
            int memberId = Integer.parseInt(payload.get("memberId").toString());
            int daysDue = payload.containsKey("daysDue") ? Integer.parseInt(payload.get("daysDue").toString()) : 14;
            String notes = payload.containsKey("notes") && payload.get("notes") != null ? payload.get("notes").toString() : "";

            boolean success = issueDao.issueBook(bookId, memberId, daysDue, notes);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Book issued successfully!"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Could not issue book."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", e.getMessage()));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Invalid request parameters."));
        }
    }

    @PostMapping("/return")
    public ResponseEntity<?> returnBook(@RequestBody Map<String, Object> payload) {
        if (!payload.containsKey("issueId")) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "issueId is required."));
        }

        try {
            int issueId = Integer.parseInt(payload.get("issueId").toString());
            Double overrideFine = payload.containsKey("fineAmount") && payload.get("fineAmount") != null 
                    ? Double.parseDouble(payload.get("fineAmount").toString()) 
                    : null;

            Map<String, Object> result = issueDao.returnBook(issueId, overrideFine);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", e.getMessage()));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Invalid request parameters."));
        }
    }
}
