package com.library.controller;

import com.library.dao.IssueDao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Map;

/**
 * REST Controller for Dashboard Metrics.
 */
@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    private final IssueDao issueDao;

    public StatsController(IssueDao issueDao) {
        this.issueDao = issueDao;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() throws SQLException {
        return ResponseEntity.ok(issueDao.getDashboardStats());
    }
}
