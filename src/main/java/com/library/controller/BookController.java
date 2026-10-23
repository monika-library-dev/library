package com.library.controller;

import com.library.dao.BookDao;
import com.library.model.Book;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Book operations.
 */
@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")
public class BookController {

    private final BookDao bookDao;

    public BookController(BookDao bookDao) {
        this.bookDao = bookDao;
    }

    @GetMapping
    public ResponseEntity<List<Book>> getBooks(@RequestParam(value = "search", required = false) String search) throws SQLException {
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(bookDao.searchBooks(search.trim()));
        }
        return ResponseEntity.ok(bookDao.getAllBooks());
    }

    @PostMapping
    public ResponseEntity<?> addBook(@RequestBody Book book) {
        if (book.getTitle() == null || book.getTitle().trim().isEmpty() ||
            book.getIsbn() == null || book.getIsbn().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Title and ISBN are required."));
        }
        if (book.getTotalCopies() <= 0) {
            book.setTotalCopies(1);
        }
        book.setAvailableCopies(book.getTotalCopies());

        try {
            boolean success = bookDao.addBook(book);
            if (success) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "success", true,
                        "message", "Book added successfully!",
                        "book", book
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Failed to add book. ISBN may already exist."));
            }
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteBook(@RequestParam("id") int id) {
        try {
            boolean success = bookDao.deleteBook(id);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Book deleted successfully."));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Book not found with ID: " + id));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        }
    }
}
