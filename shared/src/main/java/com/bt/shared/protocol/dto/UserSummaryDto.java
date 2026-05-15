package com.bt.shared.protocol.dto;

import com.bt.shared.UserRole;

import java.time.LocalDateTime;

/**
 * Tóm tắt thông tin user dùng cho admin panel.
 *
 * Không bao gồm password hash — đảm bảo không leak credential ra wire.
 */
public class UserSummaryDto {

    private long userId;
    private String username;
    private String email;
    private UserRole role;
    private boolean active;
    private double accountBalance;   // chỉ có ý nghĩa khi role = BIDDER
    private double sellerRating;     // chỉ có ý nghĩa khi role = SELLER
    private int accessLevel;         // chỉ có ý nghĩa khi role = ADMIN
    private LocalDateTime createdAt;

    public UserSummaryDto() { /* Gson */ }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public double getAccountBalance() { return accountBalance; }
    public void setAccountBalance(double accountBalance) { this.accountBalance = accountBalance; }

    public double getSellerRating() { return sellerRating; }
    public void setSellerRating(double sellerRating) { this.sellerRating = sellerRating; }

    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
