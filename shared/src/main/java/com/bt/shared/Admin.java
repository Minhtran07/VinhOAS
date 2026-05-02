package com.bt.shared;

/**
 * Quản trị viên hệ thống.
 *
 * Đặc thù:
 *  - {@code accessLevel}: quy ước cấp quyền nội bộ.
 *      1 = Moderator (xem báo cáo, xử lý tranh chấp)
 *      3 = Admin     (mở/đóng phiên, quản lý user)
 *      5 = SuperAdmin (cấu hình hệ thống, backup DB)
 */
public class Admin extends User {

    private static final long serialVersionUID = 1L;

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;

    private int accessLevel;

    public Admin() {
        super();
    }

    public Admin(String username, String email, String password, int accessLevel) {
        super(username, email, password);
        setAccessLevel(accessLevel);
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    /** Set không validate — clamp [1,5]. Dùng cho DAO. */
    public void setAccessLevelRaw(int accessLevel) {
        this.accessLevel = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, accessLevel));
    }

    public void setAccessLevel(int accessLevel) {
        if (accessLevel < MIN_LEVEL || accessLevel > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Access level phải nằm trong [" + MIN_LEVEL + ", " + MAX_LEVEL
                            + "], nhận: " + accessLevel);
        }
        this.accessLevel = accessLevel;
    }

    public boolean isSuperAdmin() {
        return accessLevel == MAX_LEVEL;
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ AccessLevel: " + accessLevel
                + (isSuperAdmin() ? " (SUPER_ADMIN)" : ""));
    }
}
