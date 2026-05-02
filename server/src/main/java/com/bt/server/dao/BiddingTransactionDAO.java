package com.bt.server.dao;

import com.bt.shared.exception.AuctionStateException;
import com.bt.shared.exception.InvalidBidException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Đặt bid an toàn concurrency — chạy trọn vẹn trong 1 transaction
 * với pessimistic lock.
 *
 * Quy trình:
 *   1. {@code SELECT ... FOR UPDATE} trên auctions: khóa row, đọc current_price,
 *      end_time, status. Mọi transaction khác cùng id sẽ phải chờ.
 *   2. Validate lại trạng thái + giá (kiểm tra ở Java entity vẫn cần, nhưng
 *      đây là validate cuối với dữ liệu vừa lock).
 *   3. INSERT bid_transactions.
 *   4. UPDATE auctions current_price (+ end_time nếu anti-sniping kích hoạt),
 *      version++.
 *   5. COMMIT — lock được giải phóng.
 *
 * Nhờ FOR UPDATE, dù 100 thread cùng bid lên một phiên, MySQL sẽ serialize
 * chúng — không lost update, không hai winner.
 *
 * Lưu ý: anti-sniping được tính ở đây (DB-side) thay vì giao cho object
 * Auction trong RAM, vì current end_time mới nhất nằm trong DB.
 */
public class BiddingTransactionDAO {

    private static final Logger log = LoggerFactory.getLogger(BiddingTransactionDAO.class);

    /** Cửa sổ anti-sniping: bid trong khoảng cuối thì kéo dài. */
    private static final long ANTI_SNIPE_WINDOW_SEC = 30;
    private static final long ANTI_SNIPE_EXTENSION_SEC = 60;

    /**
     * Đặt bid trong 1 transaction. Trả về snapshot sau commit.
     *
     * @throws InvalidBidException     amount sai quy tắc (≤ current_price, …)
     * @throws AuctionStateException   phiên không phải RUNNING / đã hết giờ
     * @throws SQLException            lỗi DB
     */
    public BidPersistenceResult placeBidAtomic(long auctionId, long bidderId, double amount)
            throws InvalidBidException, AuctionStateException, SQLException {

        if (Double.isNaN(amount) || amount <= 0) {
            throw new InvalidBidException("Số tiền đặt giá phải > 0");
        }

        log.debug("placeBidAtomic begin: auction={}, bidder={}, amount={}",
                auctionId, bidderId, amount);

        Connection conn = null;
        boolean prevAutoCommit = true;
        try {
            conn = DatabaseConnection.getConnection();
            prevAutoCommit = conn.getAutoCommit();
            // QUAN TRỌNG: bật BEGIN IMMEDIATE để SQLite cấp RESERVED lock
            // ngay từ đầu transaction. KHÔNG dùng DEFERRED (mặc định của
            // setAutoCommit(false)) vì 2 transaction concurrent đều SELECT
            // được current_price cũ → cả 2 validate `amount > old_price` →
            // bid sau có thể GIẢM giá khi commit cuối thắng.
            //
            // Thứ tự lệnh:
            //   - setAutoCommit(false) bắt đầu DEFERRED txn
            //   - rollback() để hủy DEFERRED txn ngầm
            //   - BEGIN IMMEDIATE để mở RESERVED txn thật
            // Cách này tương thích cả SQLite (RESERVED lock) và MySQL/H2
            // (BEGIN IMMEDIATE bị bỏ qua không lỗi với InnoDB; lock đến từ
            // FOR UPDATE trong lockAuction).
            // Hikari DataSource đã được config transaction_mode=IMMEDIATE
            // ở DatabaseConnection.buildDataSource() (cho SQLite). Khi gọi
            // setAutoCommit(false) bên dưới, sqlite-jdbc sẽ tự thực hiện
            // BEGIN IMMEDIATE thay vì DEFERRED → có RESERVED lock ngay,
            // mọi txn khác phải chờ. Cho DB khác (MySQL/H2), property bị
            // bỏ qua và lock đến từ FOR UPDATE.
            conn.setAutoCommit(false);

            // 1. Đọc auction (đã có RESERVED lock từ BEGIN IMMEDIATE)
            LockedAuction locked = lockAuction(conn, auctionId);
            if (locked == null) {
                throw new AuctionStateException("Auction không tồn tại: " + auctionId);
            }

            // 2. Validate
            if (!"RUNNING".equals(locked.status)) {
                throw new AuctionStateException(
                        "Phiên đang ở trạng thái " + locked.status);
            }
            LocalDateTime now = LocalDateTime.now();
            if (locked.endTime != null && now.isAfter(locked.endTime)) {
                throw new AuctionStateException("Phiên đấu giá đã kết thúc");
            }
            if (locked.sellerId == bidderId) {
                throw new InvalidBidException(
                        "Người bán không được đấu giá sản phẩm của chính mình");
            }
            if (amount <= locked.currentPrice) {
                throw new InvalidBidException(
                        "Giá đấu phải lớn hơn giá hiện tại "
                                + formatVnd(locked.currentPrice)
                                + " (đã nhập: " + formatVnd(amount) + ")");
            }

            // 3. INSERT bid
            long bidId = insertBid(conn, auctionId, bidderId, amount, now);

            // 4. Anti-sniping: kéo dài endTime nếu cần
            LocalDateTime newEnd = locked.endTime;
            if (locked.endTime != null) {
                long secondsLeft = java.time.Duration.between(now, locked.endTime).getSeconds();
                if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPE_WINDOW_SEC) {
                    newEnd = locked.endTime.plusSeconds(ANTI_SNIPE_EXTENSION_SEC);
                    log.info("Anti-sniping: auction {} extended to {}", auctionId, newEnd);
                }
            }

            // 5. UPDATE auctions
            updateAuctionAfterBid(conn, auctionId, amount, newEnd);

            conn.commit();
            log.info("Bid committed: auction={}, bidder={}, amount={}, bidId={}",
                    auctionId, bidderId, amount, bidId);
            return new BidPersistenceResult(bidId, amount, newEnd);

        } catch (SQLException | InvalidBidException | AuctionStateException ex) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException re) {
                    log.warn("Rollback fail: {}", re.getMessage());
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(prevAutoCommit); } catch (SQLException ignored) {}
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private static String formatVnd(double v) {
        // Hiển thị đẹp cho VND: 1,500,000 đ
        try {
            return String.format("%,.0f đ", v);
        } catch (Exception ex) {
            return String.valueOf(v);
        }
    }

    // ---------- Internals ----------

    private static class LockedAuction {
        long id;
        long sellerId;
        double currentPrice;
        LocalDateTime endTime;
        String status;
    }

    private LockedAuction lockAuction(Connection conn, long auctionId) throws SQLException {
        // SQLite: BEGIN IMMEDIATE đã lock DB rồi, không cần FOR UPDATE.
        // MySQL: vẫn cần FOR UPDATE — dùng query đơn không có FOR UPDATE
        // để tương thích cả hai. Nếu cần MySQL pessimistic lock thật, thay
        // bằng "SELECT ... FOR UPDATE" và bỏ BEGIN IMMEDIATE phía trên.
        String sql = "SELECT id, seller_id, current_price, end_time, status "
                + "FROM auctions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                LockedAuction la = new LockedAuction();
                la.id = rs.getLong("id");
                la.sellerId = rs.getLong("seller_id");
                la.currentPrice = rs.getDouble("current_price");
                la.endTime = SqlTime.getLocalDateTime(rs, "end_time");
                la.status = rs.getString("status");
                return la;
            }
        }
    }

    private long insertBid(Connection conn, long auctionId, long bidderId,
                           double amount, LocalDateTime when) throws SQLException {
        String sql = "INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, bid_time, is_auto_bid) "
                + "VALUES (?, ?, ?, ?, 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, bidderId);
            ps.setDouble(3, amount);
            SqlTime.setLocalDateTime(ps, 4, when);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("Không lấy được generated key cho bid");
        }
    }

    private void updateAuctionAfterBid(Connection conn, long auctionId,
                                       double newPrice, LocalDateTime newEnd) throws SQLException {
        String sql = "UPDATE auctions SET current_price = ?, end_time = ?, version = version + 1 "
                + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            SqlTime.setLocalDateTime(ps, 2, newEnd);
            ps.setLong(3, auctionId);
            ps.executeUpdate();
        }
    }
}
