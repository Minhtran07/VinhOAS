package com.bt.server.dao;

/**
 * @deprecated Đã thay thế bởi {@link DaoSmokeTest} — bao trùm rộng hơn.
 *             Giữ class rỗng để tránh break import từ chỗ khác (nếu có)
 *             và sẽ được xóa hẳn ở Phase 9 khi chuyển sang JUnit.
 */
@Deprecated
public class TestUser {
    public static void main(String[] args) {
        System.out.println("TestUser đã deprecated. Dùng DaoSmokeTest thay thế:");
        System.out.println("  mvn -pl server exec:java "
                + "-Dexec.mainClass=com.bt.server.dao.DaoSmokeTest");
    }
}
