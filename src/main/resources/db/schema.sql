-- ====================================================================
-- Library Book Issue System - Database Schema & Initial Data
-- ====================================================================

CREATE DATABASE IF NOT EXISTS library_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE library_db;

-- 1. Books Table
CREATE TABLE IF NOT EXISTS books (
    id INT AUTO_INCREMENT PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    total_copies INT NOT NULL DEFAULT 1,
    available_copies INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Members Table
CREATE TABLE IF NOT EXISTS members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    member_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(25),
    membership_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 3. Book Issues Table
CREATE TABLE IF NOT EXISTS book_issues (
    id INT AUTO_INCREMENT PRIMARY KEY,
    book_id INT NOT NULL,
    member_id INT NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE NULL,
    fine_amount DECIMAL(8,2) DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    notes VARCHAR(255),
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

-- ====================================================================
-- Sample Seed Data
-- ====================================================================

-- Insert Sample Books if table is empty
INSERT IGNORE INTO books (id, isbn, title, author, category, total_copies, available_copies) VALUES
(1, '978-0132350884', 'Clean Code', 'Robert C. Martin', 'Software Engineering', 5, 4),
(2, '978-0201633610', 'Design Patterns', 'Erich Gamma et al.', 'Architecture', 3, 2),
(3, '978-0134685991', 'Effective Java (3rd Edition)', 'Joshua Bloch', 'Java Programming', 4, 4),
(4, '978-0262033848', 'Introduction to Algorithms', 'Thomas H. Cormen', 'Computer Science', 2, 1),
(5, '978-0321356680', 'Effective Java (2nd Edition)', 'Joshua Bloch', 'Java Programming', 2, 2),
(6, '978-0596007126', 'Head First Design Patterns', 'Eric Freeman', 'Software Engineering', 4, 4);

-- Insert Sample Members
INSERT IGNORE INTO members (id, member_code, name, email, phone, membership_date, status) VALUES
(1, 'MEM-1001', 'Alex Johnson', 'alex.j@example.com', '9876543210', '2026-01-15', 'ACTIVE'),
(2, 'MEM-1002', 'Priya Sharma', 'priya.s@example.com', '9811223344', '2026-02-10', 'ACTIVE'),
(3, 'MEM-1003', 'Liam Smith', 'liam.smith@example.com', '9700112233', '2026-03-01', 'ACTIVE');

-- Insert Sample Issues (one active, one returned)
INSERT IGNORE INTO book_issues (id, book_id, member_id, issue_date, due_date, return_date, fine_amount, status, notes) VALUES
(1, 1, 1, DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL, 0.00, 'ISSUED', 'First borrow'),
(2, 2, 2, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 6 DAY), NULL, 12.00, 'ISSUED', 'Overdue issue'),
(3, 4, 3, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 16 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY), 0.00, 'RETURNED', 'Returned on time');
