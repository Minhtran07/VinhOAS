package com.bt.server.service;

import com.bt.server.dao.DatabaseConnection;
import com.bt.server.dao.SqlTime;
import com.bt.server.dao.UserDAO;
import com.bt.server.event.ConnectionRegistry;
import com.bt.shared.User;
import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.dto.AuctionPaidEvent;
import com.bt.shared.protocol.dto.PayAuctionResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Xử lý thanh toán phiên đấu giá đã FINISHED.
 *
 * Tất cả bước (validate, deduct balance, set PAID) đều chạy trong 1 transaction
 * IMMEDIATE để đảm bảo:
 *  - Bidder không thể double-pay (race 2 click thanh toán cùng lúc)
 *  - Balance không âm khi nhiều phiên thanh toán đồng thời
 *  - Status atomic chuyển FINISHED → PAID, không có window trung gian
 */
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserDAO userDAO;
    private final ConnectionRegistry registry;

    public PaymentService(UserDAO userDAO, ConnectionRegistry registry) {
        this.userDAO = userDAO;
        this.registry = registry;
    }

    /**
     * Thanh toán phiên. Trả response chứa balance mới + thời điểm.
     *
     * @param auctionId  phiên cần thanh toán
     * @param bidderId   user trong session (đã được router xác thực)
     */
    public PayAuctionResponse pay(long auctionId, long bidderId)
            throws ValidationException, AuctionStateException {

        Connection c = null;
        try {
            c = DatabaseConnection.getConnection();
            c.setAutoCommit(false); // SQLite IMMEDIATE từ pool config

            // 1. Lock + đọc phiên
            AuctionRow ar = lockAuction(c, auctionId);
            if (ar == null) {
                throw new ValidationException("Phiên không tồn tại: " + auctionId);
            }

            // 2. Validate trạng thái + winner
            if ("PAID".equals(ar.status)) {
                throw new AuctionStateException("Phiên đã được thanh toán trước đó");
            }
            if (!"FINISHED".equals(ar.status)) {
                throw new AuctionStateException(
                        "Chỉ phiên FINISHED mới thanh toán được (đang " + ar.status + ")");
            }
            if (ar.winnerId == null || ar.winnerId != bidderId) {
                throw new ValidationException(
                        "Bạn không phải người thắng phiên này");
            }

            // 3. Lock + đọc balance bidder
            BalanceRow br = lockBidderBalance(c, bidderId);
            if (br == null) {
                throw new ValidationException("Bidder không tồn tại");
            }
            double price = ar.currentPrice;
            if (br.balance < price) {
                throw new ValidationException(
                        "Số dư không đủ. Cần " + formatVnd(price)
                                + ", hiện có " + formatVnd(br.balance));
            }

            // 4. Trừ balance
            double newBalance = br.balance - price;
            updateBalance(c, bidderId, newBalance);

            // 5. Cập nhật auction → PAID
            LocalDateTime paidAt = LocalDateTime.now();
            updateAuctionPaid(c, auctionId, price, paidAt);

            c.commit();
            log.info("Payment OK: auction={} bidder={} amount={} newBalance={}",
                    auctionId, bidderId, price, newBalance);

            // 6. Push event (ngoài transaction)
            String winnerUsername = userDAO.findById(bidderId)
                    .map(User::getUsername).orElse(null);
            AuctionPaidEvent ev = new AuctionPaidEvent();
            ev.setAuctionId(auctionId);
            ev.setWinnerBidderId(bidderId);
            ev.setWinnerUsername(winnerUsername);
            ev.setPaidAmount(price);
            ev.setPaidAt(paidAt);
            registry.broadcast(MessageType.AUCTION_PAID_EVENT, ev);

            PayAuctionResponse resp = new PayAuctionResponse();
            resp.setAuctionId(auctionId);
            resp.setPaidAmount(price);
            resp.setNewBalance(newBalance);
            resp.setPaidAt(paidAt);
            return resp;

        } catch (SQLException ex) {
            if (c != null) try { c.rollback(); } catch (SQLException ignore) {}
            log.error("Payment DB error", ex);
            throw new ValidationException("Lỗi DB khi thanh toán: " + ex.getMessage());
        } catch (ValidationException | AuctionStateException ex) {
            if (c != null) try { c.rollback(); } catch (SQLException ignore) {}
            throw ex;
        } finally {
            if (c != null) {
                try { c.setAutoCommit(true); } catch (SQLException ignore) {}
                try { c.close(); } catch (SQLException ignore) {}
            }
        }
    }

    public Optional<Double> getCurrentBalance(long bidderId) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT account_balance FROM users WHERE id = ?")) {
            ps.setLong(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getDouble(1));
            }
        } catch (SQLException ex) {
            log.warn("Read balance fail: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    // ---------- Internals ----------

    private static class AuctionRow {
        String status;
        Long winnerId;
        double currentPrice;
    }

    private static class BalanceRow {
        double balance;
    }

    private AuctionRow lockAuction(Connection c, long auctionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT status, winner_bidder_id, current_price "
                        + "FROM auctions WHERE id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AuctionRow ar = new AuctionRow();
                ar.status = rs.getString("status");
                long wid = rs.getLong("winner_bidder_id");
                ar.winnerId = rs.wasNull() ? null : wid;
                ar.currentPrice = rs.getDouble("current_price");
                return ar;
            }
        }
    }

    private BalanceRow lockBidderBalance(Connection c, long bidderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT account_balance FROM users WHERE id = ?")) {
            ps.setLong(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                BalanceRow br = new BalanceRow();
                br.balance = rs.getDouble("account_balance");
                return br;
            }
        }
    }

    private void updateBalance(Connection c, long bidderId, double newBalance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET account_balance = ? WHERE id = ?")) {
            ps.setDouble(1, newBalance);
            ps.setLong(2, bidderId);
            ps.executeUpdate();
        }
    }

    private void updateAuctionPaid(Connection c, long auctionId,
                                    double amount, LocalDateTime paidAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE auctions SET status = 'PAID', paid_at = ?, paid_amount = ?, "
                        + "version = version + 1 WHERE id = ?")) {
            SqlTime.setLocalDateTime(ps, 1, paidAt);
            ps.setDouble(2, amount);
            ps.setLong(3, auctionId);
            ps.executeUpdate();
        }
    }

    private static String formatVnd(double v) {
        try { return String.format("%,.0f đ", v); }
        catch (Exception ex) { return String.valueOf(v); }
    }
}
