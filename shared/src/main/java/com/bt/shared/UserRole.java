package com.bt.shared;

/**
 * Vai trò của người dùng trong hệ thống.
 *
 * Dùng enum thay vì String để:
 *  - Compile-time check: không gõ sai "ADMIN"/"admin"/"Admim"
 *  - Switch exhaustive: compiler cảnh báo khi thêm role mới mà quên xử lý
 *  - Lưu DB dùng name() (chuỗi viết hoa) — bền và đọc được
 */
public enum UserRole {
    BIDDER,
    SELLER,
    ADMIN;

    /**
     * Parse an toàn từ String (dữ liệu DB / network) sang enum.
     * Trả về null nếu chuỗi không khớp role nào — tầng caller tự xử lý.
     */
    public static UserRole fromString(String raw) {
        if (raw == null) return null;
        try {
            return UserRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
