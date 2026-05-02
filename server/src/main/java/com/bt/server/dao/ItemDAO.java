package com.bt.server.dao;

import com.bt.shared.Art;
import com.bt.shared.Electronics;
import com.bt.shared.Item;
import com.bt.shared.ItemCategory;
import com.bt.shared.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO cho bảng {@code items} (Single Table Inheritance).
 *
 * Khi insert: chỉ set cột thuộc category tương ứng, các cột khác để NULL.
 * Khi load: dựa vào cột {@code category} để map sang đúng subclass của
 * {@link Item} ({@link Electronics} / {@link Art} / {@link Vehicle}).
 */
public class ItemDAO {

    /** Insert item mới. Trả lại item với id do DB cấp. */
    public Optional<Item> insert(Item item) {
        if (item.getSellerId() == null) {
            throw new IllegalArgumentException("Item phải có sellerId trước khi lưu");
        }
        String sql = "INSERT INTO items (name, description, starting_price, category, seller_id, "
                + "brand, warranty_months, artist, year_created, make, model, mileage) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setDouble(3, item.getStartingPrice());
            ps.setString(4, item.getCategory().name());
            ps.setLong(5, item.getSellerId());

            // Mặc định null cho các cột phụ
            ps.setNull(6, Types.VARCHAR);
            ps.setNull(7, Types.INTEGER);
            ps.setNull(8, Types.VARCHAR);
            ps.setNull(9, Types.INTEGER);
            ps.setNull(10, Types.VARCHAR);
            ps.setNull(11, Types.VARCHAR);
            ps.setNull(12, Types.INTEGER);

            switch (item.getCategory()) {
                case ELECTRONICS: {
                    Electronics e = (Electronics) item;
                    ps.setString(6, e.getBrand());
                    ps.setInt(7, e.getWarrantyMonths());
                    break;
                }
                case ART: {
                    Art a = (Art) item;
                    ps.setString(8, a.getArtist());
                    ps.setInt(9, a.getYearCreated());
                    break;
                }
                case VEHICLE: {
                    Vehicle v = (Vehicle) item;
                    ps.setString(10, v.getMake());
                    ps.setString(11, v.getModel());
                    ps.setInt(12, v.getMileage());
                    break;
                }
            }

            if (ps.executeUpdate() == 0) return Optional.empty();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getLong(1));
                    return Optional.of(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Item> findById(long id) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT * FROM items WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Item> findBySeller(long sellerId) {
        return queryList("SELECT * FROM items WHERE seller_id = ? ORDER BY id DESC",
                ps -> ps.setLong(1, sellerId));
    }

    public List<Item> findAll() {
        return queryList("SELECT * FROM items ORDER BY id DESC", ps -> {});
    }

    public boolean update(Item item) {
        if (item.getId() == null) {
            throw new IllegalArgumentException("Update cần item có id");
        }
        String sql = "UPDATE items SET name = ?, description = ?, starting_price = ? "
                + "WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setDouble(3, item.getStartingPrice());
            ps.setLong(4, item.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(long id) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM items WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra item đã được đấu giá bán hoặc đang đấu giá.
     *
     * Quy tắc nghiệp vụ (kiểu eBay):
     *  - OPEN / RUNNING                      → đang chạy, không cho tạo phiên mới
     *  - FINISHED / PAID có winner_bidder_id → đã bán xong, không cho bán lại
     *  - FINISHED không có winner            → ế, có thể bán lại
     *  - CANCELED                            → bị hủy, có thể bán lại
     *
     * Nghĩa là: chặn tạo phiên mới nếu có ÍT NHẤT 1 phiên đang chạy HOẶC
     * phiên đã kết thúc có người thắng.
     */
    public boolean hasActiveAuction(long itemId) {
        String sql = "SELECT COUNT(*) FROM auctions WHERE item_id = ? "
                + "AND ("
                + "  status IN ('OPEN','RUNNING') "
                + "  OR (status IN ('FINISHED','PAID') AND winner_bidder_id IS NOT NULL)"
                + ")";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ---------- Helpers ----------

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<Item> queryList(String sql, Binder binder) {
        List<Item> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Item mapRow(ResultSet rs) throws SQLException {
        ItemCategory cat = ItemCategory.fromString(rs.getString("category"));
        if (cat == null) {
            throw new SQLException("Category không hợp lệ trong DB row");
        }
        // Dùng constructor rỗng + raw setter để tránh validate chặn data legacy
        Item item;
        switch (cat) {
            case ELECTRONICS: {
                Electronics e = new Electronics();
                e.setBrandRaw(rs.getString("brand"));
                e.setWarrantyMonthsRaw(rs.getInt("warranty_months"));
                item = e;
                break;
            }
            case ART: {
                Art a = new Art();
                a.setArtistRaw(rs.getString("artist"));
                a.setYearCreatedRaw(rs.getInt("year_created"));
                item = a;
                break;
            }
            case VEHICLE: {
                Vehicle v = new Vehicle();
                v.setMakeRaw(rs.getString("make"));
                v.setModelRaw(rs.getString("model"));
                v.setMileageRaw(rs.getInt("mileage"));
                item = v;
                break;
            }
            default:
                throw new SQLException("Category chưa hỗ trợ: " + cat);
        }
        item.setNameRaw(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setStartingPriceRaw(rs.getDouble("starting_price"));
        item.setId(rs.getLong("id"));
        item.setSellerId(rs.getLong("seller_id"));
        java.time.LocalDateTime createdAt = SqlTime.getLocalDateTime(rs, "created_at");
        if (createdAt != null) item.setCreatedAt(createdAt);
        return item;
    }
}
