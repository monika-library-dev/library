package com.library.controller;

import com.library.dao.MemberDao;
import com.library.model.Member;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Member operations.
 */
@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "*")
public class MemberController {

    private final MemberDao memberDao;

    public MemberController(MemberDao memberDao) {
        this.memberDao = memberDao;
    }

    @GetMapping
    public ResponseEntity<List<Member>> getMembers(@RequestParam(value = "search", required = false) String search) throws SQLException {
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(memberDao.searchMembers(search.trim()));
        }
        return ResponseEntity.ok(memberDao.getAllMembers());
    }

    @PostMapping
    public ResponseEntity<?> addMember(@RequestBody Member member) {
        if (member.getName() == null || member.getName().trim().isEmpty() ||
            member.getEmail() == null || member.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Name and Email are required."));
        }
        if (member.getMemberCode() == null || member.getMemberCode().trim().isEmpty()) {
            member.setMemberCode("MEM-" + System.currentTimeMillis() % 100000);
        }

        try {
            boolean success = memberDao.addMember(member);
            if (success) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "success", true,
                        "message", "Member registered successfully!",
                        "member", member
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", true, "message", "Failed to register member. Email or Code may already exist."));
            }
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteMember(@RequestParam("id") int id) {
        try {
            boolean success = memberDao.deleteMember(id);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Member deleted successfully."));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", true, "message", "Member not found with ID: " + id));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true, "message", "Database error: " + e.getMessage()));
        }
    }
}
