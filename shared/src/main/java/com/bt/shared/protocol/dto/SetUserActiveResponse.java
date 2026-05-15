package com.bt.shared.protocol.dto;

/**
 * Response cho {@link SetUserActiveRequest}.
 *
 * Trả về trạng thái MỚI của user sau khi update — client dùng để refresh row
 * mà không phải reload toàn bộ danh sách.
 */
public class SetUserActiveResponse {

    private long userId;
    private boolean active;
    private boolean success;

    public SetUserActiveResponse() { /* Gson */ }

    public SetUserActiveResponse(long userId, boolean active, boolean success) {
        this.userId = userId;
        this.active = active;
        this.success = success;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
