package com.bt.server.dao;

import com.bt.shared.Admin;
import com.bt.shared.Bidder;
import com.bt.shared.Seller;
import com.bt.shared.User;
import com.bt.shared.UserRole;
import com.bt.shared.protocol.dto.UserSummaryDto;

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

    /**
     * Liệt kê user cho admin panel — trả về DTO trực tiếp (kèm is_active +
     * created_at). Tách method riêng để tránh phải gắn field {@code active}
     * vào entity User chỉ phục vụ admin.
     *
     * Filter: roleFilter null = mọi role, activeFilter null = mọi trạng thái.
     */
    public List<UserSummaryDto> listForAdmin(UserRole roleFilter, Boolean activeFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        if (roleFilter != null) sql.append(" AND role = ?");
        if (activeFilter != null) sql.append(" AND is_active = ?");
        sql.append(" ORDER BY id ASC");

        List<UserSummaryDto> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = 1;
            if (roleFilter != null) {
                ps.setString(idx++, roleFilter.name());
            }
            if (activeFilter != null) {
                ps.setInt(idx++, activeFilter ? 1 : 0);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapSummary(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] listForAdmin fail: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private UserSummaryDto mapSummary(ResultSet rs) throws SQLException {
        UserSummaryDto d = new UserSummaryDto();
        d.setUserId(rs.getLong("id"));
        d.setUsername(rs.getString("username"));
        d.setEmail(rs.getString("email"));
        UserRole role = UserRole.fromString(rs.getString("role"));
        d.setRole(role == null ? UserRole.BIDDER : role);
        // is_active default 1 nếu NULL (cột cũ trước migration)
        int activeInt = rs.getInt("is_active");
        d.setActive(rs.wasNull() ? true : activeInt == 1);
        d.setAccountBalance(rs.getDouble("account_balance"));
        d.setSellerRating(rs.getDouble("seller_rating"));
        d.setAccessLevel(safeInt(rs, "access_level", 1));
        d.setCreatedAt(SqlTime.getLocalDateTime(rs, "created_at"));
        return d;
    }

    /** Lấy trạng thái active hiện tại của 1 user — admin dùng để verify sau update. */
    public Optional<Boolean> isActive(long userId) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT is_active FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return Optional.of(rs.wasNull() ? true : v == 1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
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
