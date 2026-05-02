package com.bt.server.service;

import com.bt.server.dao.DatabaseConnection;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.dto.RateSellerResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Cho phép bidder đã PAID đánh giá seller (1-5 sao + comment).
 *
 * Quy tắc:
 *  - Phiên phải PAID
 *  - Bidder phải là winner
 *  - 1 bidder chỉ rate 1 lần / phiên (UNIQUE constraint)
 *  - Sau khi insert, recompute avg → cập nhật users.seller_rating
 */
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    public RateSellerResponse rate(long auctionId, long bidderId,
                                   int stars, String comment)
            throws ValidationException {
        if (stars < 1 || stars > 5) {
            throw new ValidationException("Stars phải từ 1 đến 5");
        }
        if (comment != null && comment.length() > 1000) {
            throw new ValidationException("Comment tối đa 1000 ký tự");
        }

        Connection c = null;
        try {
            c = DatabaseConnection.getConnection();
            c.setAutoCommit(false);

            // 1. Validate auction PAID + winner
            long sellerId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seller_id, status, winner_bidder_id "
                            + "FROM auctions WHERE id = ?")) {
                ps.setLong(1, auctionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ValidationException("Phiên không tồn tại");
                    }
                    String st = rs.getString("status");
                    if (!"PAID".equals(st)) {
                        throw new ValidationException(
                                "Chỉ phiên đã PAID mới được rate (đang " + st + ")");
                    }
                    long wid = rs.getLong("winner_bidder_id");
                    if (rs.wasNull() || wid != bidderId) {
                        throw new ValidationException(
                                "Bạn không phải winner của phiên này");
                    }
                    sellerId = rs.getLong("seller_id");
                }
            }

            // 2. Check chưa rate
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM seller_ratings "
                            + "WHERE auction_id = ? AND bidder_id = ?")) {
                ps.setLong(1, auctionId);
                ps.setLong(2, bidderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ValidationException("Bạn đã đánh giá phiên này rồi");
                    }
                }
            }

            // 3. Insert rating
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO seller_ratings "
                            + "(auction_id, seller_id, bidder_id, stars, comment) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                ps.setLong(1, auctionId);
                ps.setLong(2, sellerId);
                ps.setLong(3, bidderId);
                ps.setInt(4, stars);
                ps.setString(5, comment);
                ps.executeUpdate();
            }

            // 4. Recompute avg + count
            double avg;
            int total;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT AVG(stars) AS avg_stars, COUNT(*) AS total "
                            + "FROM seller_ratings WHERE seller_id = ?")) {
                ps.setLong(1, sellerId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    avg = rs.getDouble("avg_stars");
                    total = rs.getInt("total");
                }
            }

            // 5. Update users.seller_rating
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE users SET seller_rating = ? WHERE id = ?")) {
                ps.setDouble(1, avg);
                ps.setLong(2, sellerId);
                ps.executeUpdate();
            }

            c.commit();
            log.info("Rating saved: auction={} bidder={} seller={} stars={} avg={}",
                    auctionId, bidderId, sellerId, stars, avg);

            RateSellerResponse resp = new RateSellerResponse();
            resp.setAuctionId(auctionId);
            resp.setSellerId(sellerId);
            resp.setStars(stars);
            resp.setNewAverageRating(avg);
            resp.setTotalRatings(total);
            return resp;

        } catch (SQLException ex) {
            if (c != null) try { c.rollback(); } catch (SQLException ignore) {}
            log.error("Rating DB error", ex);
            throw new ValidationException("Lỗi DB khi rate: " + ex.getMessage());
        } catch (ValidationException ex) {
            if (c != null) try { c.rollback(); } catch (SQLException ignore) {}
            throw ex;
        } finally {
            if (c != null) {
                try { c.setAutoCommit(true); } catch (SQLException ignore) {}
                try { c.close(); } catch (SQLException ignore) {}
            }
        }
    }
}
