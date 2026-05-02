package com.bt.client.session;

import com.bt.shared.UserRole;
import com.bt.shared.protocol.dto.LoginResponse;

/**
 * Lưu thông tin user đang đăng nhập trong process JavaFX.
 *
 * Singleton đơn giản. Khi logout → {@link #clear()}; controller kiểm tra
 * {@link #isAuthenticated()} trước khi cho thao tác đặc quyền.
 */
public class Session {

    private static final Session INSTANCE = new Session();

    private long userId;
    private String username;
    private String email;
    private UserRole role;
    private double accountBalance;
    private double sellerRating;
    private int accessLevel;
    private boolean authenticated;
    /**
     * Lưu password trong RAM (không persist) chỉ để hỗ trợ reconnect tự động:
     * sau khi mạng phục hồi, ServerConnection re-login transparently. Bị xóa
     * khi user logout.
     */
    private transient String replayPassword;

    private Session() {}

    public static Session get() { return INSTANCE; }

    public synchronized void setFromLogin(LoginResponse r) {
        this.userId = r.getUserId();
        this.username = r.getUsername();
        this.email = r.getEmail();
        this.role = r.getRole();
        this.accountBalance = r.getAccountBalance();
        this.sellerRating = r.getSellerRating();
        this.accessLevel = r.getAccessLevel();
        this.authenticated = true;
    }

    public synchronized void clear() {
        this.userId = 0;
        this.username = null;
        this.email = null;
        this.role = null;
        this.authenticated = false;
        this.replayPassword = null;
    }

    /** Set bởi LoginController NGAY SAU khi login OK để reconnect dùng được. */
    public synchronized void setReplayPassword(String pwd) {
        this.replayPassword = pwd;
    }

    public synchronized String getReplayPassword() { return replayPassword; }

    // Tất cả getter đều synchronized để tránh stale read khi setFromLogin/clear
    // chạy song song với các thread (UI thread + network listener thread + bidding-init thread).
    public synchronized boolean isAuthenticated() { return authenticated; }
    public synchronized long getUserId() { return userId; }
    public synchronized String getUsername() { return username; }
    public synchronized String getEmail() { return email; }
    public synchronized UserRole getRole() { return role; }
    public synchronized double getAccountBalance() { return accountBalance; }
    public synchronized double getSellerRating() { return sellerRating; }
    public synchronized int getAccessLevel() { return accessLevel; }
}
