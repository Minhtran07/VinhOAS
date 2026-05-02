package com.bt.shared.protocol.dto;

import com.bt.shared.UserRole;

/**
 * Response trả về sau khi đăng nhập thành công.
 *
 * Không gửi cả object User để tránh leak password hash; chỉ gửi field
 * cần thiết để client lưu session và phân quyền UI.
 */
public class LoginResponse {

    private long userId;
    private String username;
    private String email;
    private UserRole role;
    private double accountBalance;   // chỉ có ý nghĩa khi role = BIDDER
    private double sellerRating;     // chỉ có ý nghĩa khi role = SELLER
    private int accessLevel;         // chỉ có ý nghĩa khi role = ADMIN

    public LoginResponse() { /* Gson */ }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public double getAccountBalance() { return accountBalance; }
    public void setAccountBalance(double accountBalance) { this.accountBalance = accountBalance; }

    public double getSellerRating() { return sellerRating; }
    public void setSellerRating(double sellerRating) { this.sellerRating = sellerRating; }

    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }
}
