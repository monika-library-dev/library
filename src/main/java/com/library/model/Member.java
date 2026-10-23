package com.library.model;

import java.time.LocalDate;

/**
 * Model representing a Library Member (Student, Faculty, etc.).
 */
public class Member {
    private int id;
    private String memberCode;
    private String name;
    private String email;
    private String phone;
    private String membershipDate;
    private String status;

    public Member() {}

    public Member(int id, String memberCode, String name, String email, String phone, String membershipDate, String status) {
        this.id = id;
        this.memberCode = memberCode;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.membershipDate = membershipDate;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getMembershipDate() { return membershipDate; }
    public void setMembershipDate(String membershipDate) { this.membershipDate = membershipDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
