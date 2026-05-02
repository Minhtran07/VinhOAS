package com.bt.shared.protocol.dto;

import com.bt.shared.UserRole;

/**
 * Đăng ký tài khoản mới. Chỉ cho phép BIDDER và SELLER tự đăng ký;
 * ADMIN phải do super admin tạo (Phase 6 sẽ enforce ở server).
 */
public class RegisterRequest {

    private String username;
    private String email;
    private String password;
    private UserRole role;

    public RegisterRequest() { /* Gson */ }

    public RegisterRequest(String username, String email, String password, UserRole role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
}
