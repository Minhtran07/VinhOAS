package com.bt.server.dao;

import com.bt.shared.Auction;
import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.Item;

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
 * DAO cho bảng {@code auctions}.
 *
 * Lưu ý:
 *  - Object {@link Auction} không trực tiếp giữ {@link Item} khi map từ DB —
 *    DAO load id rồi tầng service sẽ resolve thêm Item bằng {@link ItemDAO}
 *    nếu cần (tránh JOIN không cần thiết).
 *  - Cột {@code version} chuẩn bị cho optimistic locking ở Phase 4.
 */
public class AuctionDAO {

    private final ItemDAO itemDAO = new ItemDAO();

    public Optional<Auction> insert(Auction auction) {
        if (auction.getItem() == null || auction.getItem().getId() == null) {
            throw new IllegalArgumentException("Auction cần Item đã được lưu (có id)");
        }
        if (auction.getSellerId() == null) {
            throw new IllegalArgumentException("Auction phải có sellerId");
        }
        String sql = "INSERT INTO auctions (item_id, seller_id, start_time, end_time, "
                + "status, current_price, version) VALUES (?, ?, ?, ?, ?, ?, 0)";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, auction.getItem().getId());
            ps.setLong(2, auction.getSellerId());
            SqlTime.setLocalDateTime(ps, 3, auction.getStartTime());
            SqlTime.setLocalDateTime(ps, 4, auction.getEndTime());
            ps.setString(5, auction.getStatus().name());
            ps.setDouble(6, auction.getCurrentPrice());

            if (ps.executeUpdate() == 0) return Optional.empty();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    auction.setId(keys.getLong(1));
                    return Optional.of(auction);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Auction> findById(long id) {
        return queryOne("SELECT * FROM auctions WHERE id = ?",
                ps -> ps.setLong(1, id));
    }

    public List<Auction> findByStatus(AuctionStatus status) {
        return queryList("SELECT * FROM auctions WHERE status = ? ORDER BY end_time ASC",
                ps -> ps.setString(1, status.name()));
    }

    public List<Auction> findRunningExpired() {
        // datetime('now') chuẩn SQLite. MySQL hiểu CURRENT_TIMESTAMP.
        // Cả hai dialect cùng chấp nhận literal ISO khi so sánh.
        return queryList("SELECT * FROM auctions WHERE status = 'RUNNING' "
                + "AND end_time <= datetime('now')", ps -> {});
    }

    public List<Auction> findAll() {
        return queryList("SELECT * FROM auctions ORDER BY id DESC", ps -> {});
    }

    /**
     * Cập nhật status. Nếu chuyển sang FINISHED/PAID/CANCELED nên kèm winner_bidder_id.
     */
    public boolean updateStatus(long auctionId, AuctionStatus newStatus, Long winnerBidderId) {
        String sql = "UPDATE auctions SET status = ?, winner_bidder_id = ?, "
                + "version = version + 1 WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            if (winnerBidderId == null) ps.setNull(2, Types.BIGINT);
            else ps.setLong(2, winnerBidderId);
            ps.setLong(3, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đặt status FINISHED kèm winner — chạy trong transaction với BEGIN
     * IMMEDIATE để serialize với placeBidAtomic. Tránh race: bid đang
     * in-flight commit SAU khi scheduler đã chốt winner cũ → bid lost.
     *
     * Trả false nếu auction không còn ở trạng thái cho phép finalize
     * (vd: đã FINISHED/PAID/CANCELED bởi thread khác).
     */
    public boolean finishAuctionAtomic(long auctionId, Long winnerBidderId) {
        Connection c = null;
        try {
            c = DatabaseConnection.getConnection();
            // setAutoCommit(false) → sqlite-jdbc dùng IMMEDIATE từ pool config
            c.setAutoCommit(false);

            // Validate: chỉ finalize khi đang RUNNING
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT status FROM auctions WHERE id = ?")) {
                check.setLong(1, auctionId);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return false;
                    }
                    String currentStatus = rs.getString("status");
                    if (!"RUNNING".equals(currentStatus)) {
                        c.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE auctions SET status = 'FINISHED', winner_bidder_id = ?, "
                            + "version = version + 1 WHERE id = ? AND status = 'RUNNING'")) {
                if (winnerBidderId == null) upd.setNull(1, Types.BIGINT);
                else upd.setLong(1, winnerBidderId);
                upd.setLong(2, auctionId);
                int updated = upd.executeUpdate();
                if (updated == 0) {
                    c.rollback();
                    return false;
                }
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            if (c != null) {
                try { c.rollback(); } catch (SQLException ignore) {}
            }
            return false;
        } finally {
            if (c != null) {
                try { c.setAutoCommit(true); } catch (SQLException ignore) {}
                try { c.close(); } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Cập nhật current_price + endTime (để hỗ trợ anti-sniping mở rộng phiên).
     * Optimistic lock: chỉ update khi version khớp; trả số dòng bị ảnh hưởng.
     */
    public boolean updateCurrentPriceAndEndTime(long auctionId, double newPrice,
                                                Timestamp newEndTime, int expectedVersion) {
        // Wrapper giữ chữ ký cũ — chuyển Timestamp sang LocalDateTime để dùng String ISO
        java.time.LocalDateTime endLdt = newEndTime == null ? null : newEndTime.toLocalDateTime();
        return updateCurrentPriceAndEndTime(auctionId, newPrice, endLdt, expectedVersion);
    }

    public boolean updateCurrentPriceAndEndTime(long auctionId, double newPrice,
                                                java.time.LocalDateTime newEndTime,
                                                int expectedVersion) {
        String sql = "UPDATE auctions SET current_price = ?, end_time = ?, version = version + 1 "
                + "WHERE id = ? AND version = ?";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            SqlTime.setLocalDateTime(ps, 2, newEndTime);
            ps.setLong(3, auctionId);
            ps.setInt(4, expectedVersion);
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

    private Optional<Auction> queryOne(String sql, Binder binder) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private List<Auction> queryList(String sql, Binder binder) {
        List<Auction> list = new ArrayList<>();
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

    /**
     * Build object Auction từ row. Tự load Item kèm theo (1 query phụ),
     * tránh dùng JOIN cho đơn giản và để cache item ở tầng service sau này.
     */
    private Auction mapRow(ResultSet rs) throws SQLException {
        long itemId = rs.getLong("item_id");
        Item item = itemDAO.findById(itemId).orElse(null);

        Auction a = new Auction();
        a.setId(rs.getLong("id"));
        a.setItem(item);
        a.setSellerId(rs.getLong("seller_id"));
        a.setStartTime(SqlTime.getLocalDateTime(rs, "start_time"));
        a.setEndTime(SqlTime.getLocalDateTime(rs, "end_time"));
        a.setStatus(AuctionStatus.valueOf(rs.getString("status")));
        a.setCurrentPriceRaw(rs.getDouble("current_price"));
        java.time.LocalDateTime createdAt = SqlTime.getLocalDateTime(rs, "created_at");
        if (createdAt != null) a.setCreatedAt(createdAt);
        return a;
    }
}
