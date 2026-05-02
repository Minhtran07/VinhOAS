package com.bt.server.dao;

import com.bt.server.autobid.AutoBidConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO cho bảng auto_bid_configs. Lưu trữ cấu hình auto-bid để khôi phục
 * sau khi server restart.
 *
 * Có UNIQUE constraint (bidder_id, auction_id) — 1 bidder chỉ có 1 config
 * mỗi phiên. Đăng ký lại sẽ update.
 */
public class AutoBidDAO {

    /** Insert hoặc update — upsert thủ công cho SQLite. */
    public Optional<AutoBidConfig> upsert(long bidderId, long auctionId,
                                           double maxBid, double increment) {
        // Cách đơn giản nhất: try INSERT, nếu UNIQUE conflict thì UPDATE
        String insert = "INSERT INTO auto_bid_configs "
                + "(bidder_id, auction_id, max_bid, increment, is_active, registered_at) "
                + "VALUES (?, ?, ?, ?, 1, ?)";
        String update = "UPDATE auto_bid_configs "
                + "SET max_bid = ?, increment = ?, is_active = 1, registered_at = ? "
                + "WHERE bidder_id = ? AND auction_id = ?";
        LocalDateTime now = LocalDateTime.now();
        try (Connection c = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setLong(1, bidderId);
                ps.setLong(2, auctionId);
                ps.setDouble(3, maxBid);
                ps.setDouble(4, increment);
                SqlTime.setLocalDateTime(ps, 5, now);
                ps.executeUpdate();
                AutoBidConfig cfg = new AutoBidConfig(bidderId, auctionId,
                        maxBid, increment, now);
                return Optional.of(cfg);
            } catch (SQLException uniqueConflict) {
                // Conflict UNIQUE → update thay
                try (PreparedStatement ps = c.prepareStatement(update)) {
                    ps.setDouble(1, maxBid);
                    ps.setDouble(2, increment);
                    SqlTime.setLocalDateTime(ps, 3, now);
                    ps.setLong(4, bidderId);
                    ps.setLong(5, auctionId);
                    ps.executeUpdate();
                }
                return findByBidderAndAuction(c, bidderId, auctionId);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return Optional.empty();
    }

    public boolean deactivate(long bidderId, long auctionId) {
        String sql = "UPDATE auto_bid_configs SET is_active = 0 "
                + "WHERE bidder_id = ? AND auction_id = ?";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, bidderId);
            ps.setLong(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /** Lấy mọi config active (dùng khi server restart để re-load AutoBidEngine). */
    public List<AutoBidConfig> findAllActive() {
        List<AutoBidConfig> list = new ArrayList<>();
        String sql = "SELECT * FROM auto_bid_configs WHERE is_active = 1";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    private Optional<AutoBidConfig> findByBidderAndAuction(Connection c,
                                                           long bidderId,
                                                           long auctionId) throws SQLException {
        String sql = "SELECT * FROM auto_bid_configs "
                + "WHERE bidder_id = ? AND auction_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, bidderId);
            ps.setLong(2, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    private AutoBidConfig mapRow(ResultSet rs) throws SQLException {
        long bidderId = rs.getLong("bidder_id");
        long auctionId = rs.getLong("auction_id");
        double maxBid = rs.getDouble("max_bid");
        double increment = rs.getDouble("increment");
        LocalDateTime registeredAt = SqlTime.getLocalDateTime(rs, "registered_at");
        if (registeredAt == null) registeredAt = LocalDateTime.now();
        AutoBidConfig cfg = new AutoBidConfig(bidderId, auctionId,
                maxBid, increment, registeredAt);
        if (rs.getInt("is_active") == 0) cfg.deactivate();
        return cfg;
    }
}
