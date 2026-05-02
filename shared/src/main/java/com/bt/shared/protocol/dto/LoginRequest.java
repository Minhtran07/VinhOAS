package com.bt.shared.protocol.dto;

/**
 * Payload cho LOGIN_REQUEST.
 *
 * Lưu ý: password sẽ được hash ở client trước khi gửi (Phase 11). Hiện tại
 * vẫn truyền plaintext qua socket cho đơn giản.
 */
public class LoginRequest {

    private String username;
    private String password;

    public LoginRequest() { /* Gson */ }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
