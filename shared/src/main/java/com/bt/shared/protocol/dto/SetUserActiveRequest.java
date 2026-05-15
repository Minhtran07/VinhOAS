package com.bt.shared.protocol.dto;

/**
 * Yêu cầu ban/unban user. Admin only.
 *
 *  - {@code active = false} : ban — user không login được nữa
 *  - {@code active = true}  : unban — khôi phục quyền login
 */
public class SetUserActiveRequest {

    private long userId;
    private boolean active;

    public SetUserActiveRequest() { /* Gson */ }

    public SetUserActiveRequest(long userId, boolean active) {
        this.userId = userId;
        this.active = active;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
