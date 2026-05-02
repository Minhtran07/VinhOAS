package com.bt.server.dao;

import com.bt.shared.BidTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * DAO cho bảng {@code bid_transactions}.
 *
 * Lưu ý: BidTransaction trong DB chỉ giữ id (auctionId, bidderId), không lưu
 * cả object Bidder. Tầng service tự resolve thêm khi cần.
 */
public class BidDAO {

    private static final Logger log = LoggerFactory.getLogger(BidDAO.class);

    public Optional<BidTransaction> insert(BidTransaction bid) {
        if (bid.getAuctionId() == null || bid.getBidderId() == null) {
            throw new IllegalArgumentException("Bid phải có auctionId và bidderId");
        }
        String sql = "INSERT INTO bid_transactions "
                + "(auction_id, bidder_id, bid_amount, bid_time, is_auto_bid) "
                + "VALUES (?, ?, ?, ?, 0)";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bid.getAuctionId());
            ps.setLong(2, bid.getBidderId());
            ps.setDouble(3, bid.getBidAmount());
            SqlTime.setLocalDateTime(ps, 4, bid.getTimestamp());

            if (ps.executeUpdate() == 0) return Optional.empty();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    bid.setId(keys.getLong(1));
                    return Optional.of(bid);
                }
            }
        } catch (SQLException e) {
            log.error("BidDAO query fail: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /** Lịch sử bid theo phiên, sort tăng dần theo thời gian (cho line chart).
     *  Tie-break theo id để 2 bid cùng bid_time vẫn có thứ tự xác định
     *  (id auto-increment ↔ thứ tự commit thật trong DB). */
    public List<BidTransaction> findByAuction(long auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? "
                + "ORDER BY bid_time ASC, id ASC";
        List<BidTransaction> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("BidDAO findByAuction fail: {}", e.getMessage(), e);
        }
        return list;
    }

    /** Lấy bid cao nhất (mới nhất nếu cùng giá) — dùng cho findWinner. */
    public Optional<BidTransaction> findHighestByAuction(long auctionId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? "
                + "ORDER BY bid_amount DESC, bid_time ASC LIMIT 1";
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("BidDAO query fail: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public int countByAuction(long auctionId) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("BidDAO countByAuction fail: {}", e.getMessage(), e);
        }
        return 0;
    }

    private BidTransaction mapRow(ResultSet rs) throws SQLException {
        BidTransaction bid = new BidTransaction();
        bid.setId(rs.getLong("id"));
        bid.setAuctionId(rs.getLong("auction_id"));
        bid.setBidderId(rs.getLong("bidder_id"));
        bid.setBidAmount(rs.getDouble("bid_amount"));
        bid.setTimestamp(SqlTime.getLocalDateTime(rs, "bid_time"));
        return bid;
    }
}
