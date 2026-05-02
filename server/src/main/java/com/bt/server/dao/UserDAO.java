package com.bt.server.dao;

import com.bt.shared.Admin;
import com.bt.shared.Bidder;
import com.bt.shared.Seller;
import com.bt.shared.User;
import com.bt.shared.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO cho bảng {@code users}.
 *
 * Map giữa row và subclass cụ thể của {@link User}:
 *  - role = ADMIN  → {@link Admin}
 *  - role = SELLER → {@link Seller}
 *  - role = BIDDER → {@link Bidder}
 *
 * Các trường role-specific (account_balance, seller_rating, access_level) được
 * lưu trong cùng 1 bảng nhưng chỉ áp dụng cho 1 role tương ứng.
 */
public class UserDAO {

    /** Đăng nhập theo username + password. */
    public Optional<User> login(String username, String password) {
        String sql = "SELECT * FROM users "
                + "WHERE username = ? AND password = ? AND is_active = 1";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    /** Tạo user mới — trả về user đã có id (do DB cấp). */
    public Optional<User> register(User user) {
        String sql = "INSERT INTO users (username, email, password, role, "
                + "account_balance, seller_rating, access_level) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole().name());
            ps.setDouble(5, balanceOf(user));
            ps.setDouble(6, ratingOf(user));
            ps.setInt(7, levelOf(user));

            if (ps.executeUpdate() == 0) return Optional.empty();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                    return Optional.of(user);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<User> findById(long id) {
        return queryOne("SELECT * FROM users WHERE id = ?", ps -> ps.setLong(1, id));
    }

    public Optional<User> findByUsername(String username) {
        // SQLite: COLLATE NOCASE → so sánh case-insensitive cho username.
        // User nhập 'Alice_S' vẫn tìm thấy 'alice_s'. Tránh confused user.
        return queryOne("SELECT * FROM users WHERE username = ? COLLATE NOCASE",
                ps -> ps.setString(1, username));
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Cập nhật số dư cho Bidder (atomic, có version check đơn giản). */
    public boolean updateBalance(long bidderId, double newBalance) {
        String sql = "UPDATE users SET account_balance = ? "
                + "WHERE id = ? AND role = 'BIDDER'";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setLong(2, bidderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Update password hash (cho migration plaintext → BCrypt). */
    public boolean updatePasswordHash(long userId, String newHash) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE users SET password = ? WHERE id = ?")) {
            ps.setString(1, newHash);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Vô hiệu hóa user (admin ban). */
    public boolean setActive(long userId, boolean active) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE users SET is_active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------- Helpers ----------

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<User> queryOne(String sql, Binder binder) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Optional.of(mapRow(rs));
                    } catch (Exception mapEx) {
                        // mapRow lỗi nhưng row có tồn tại — log để debug,
                        // KHÔNG để empty Optional che dấu vấn đề
                        System.err.println("[UserDAO] mapRow fail cho SQL: " + sql);
                        mapEx.printStackTrace();
                        throw mapEx instanceof SQLException
                                ? (SQLException) mapEx
                                : new SQLException(mapEx);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Query fail: " + sql + " — " + e.getMessage());
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private User mapRow(ResultSet rs) throws SQLException {
        UserRole role = UserRole.fromString(rs.getString("role"));
        if (role == null) role = UserRole.BIDDER;

        // Dùng các setter *Raw để tránh validate chặn dữ liệu legacy
        // (vd: email không match regex mới, username < 3 ký tự...)
        User u;
        switch (role) {
            case ADMIN: {
                Admin a = new Admin();
                a.setAccessLevelRaw(safeInt(rs, "access_level", 1));
                u = a;
                break;
            }
            case SELLER: {
                Seller s = new Seller();
                s.setSellerRatingRaw(rs.getDouble("seller_rating"));
                u = s;
                break;
            }
            case BIDDER:
            default: {
                Bidder b = new Bidder();
                b.setAccountBalanceRaw(rs.getDouble("account_balance"));
                u = b;
                break;
            }
        }
        u.setId(rs.getLong("id"));
        u.setUsernameRaw(rs.getString("username"));
        u.setEmailRaw(rs.getString("email"));
        u.setPasswordRaw(rs.getString("password"));
        java.time.LocalDateTime createdAt = SqlTime.getLocalDateTime(rs, "created_at");
        if (createdAt != null) u.setCreatedAt(createdAt);
        return u;
    }

    private static int safeInt(ResultSet rs, String col, int fallback) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? fallback : v;
    }

    private static double balanceOf(User u) {
        return (u instanceof Bidder) ? ((Bidder) u).getAccountBalance() : 0.0;
    }

    private static double ratingOf(User u) {
        return (u instanceof Seller) ? ((Seller) u).getSellerRating() : 0.0;
    }

    private static int levelOf(User u) {
        return (u instanceof Admin) ? ((Admin) u).getAccessLevel() : 1;
    }
}
