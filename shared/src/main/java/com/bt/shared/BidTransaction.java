package com.bt.shared;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Một lượt đặt giá (bid) trong một phiên đấu giá.
 *
 * Thiết kế:
 *  - Lưu cả {@code bidder} (object) và {@code bidderId} (id thuần) — id thường
 *    là cái duy nhất khi load từ DB (server có thể chưa join), còn object đầy
 *    đủ dùng cho UI.
 *  - {@code auctionId}: tham chiếu phiên đấu giá. Tránh giữ tham chiếu vòng
 *    Auction ⇄ BidTransaction để serialize không bị infinite loop.
 *  - {@code timestamp} là final, set 1 lần khi tạo, không cho thay đổi —
 *    đảm bảo lịch sử bid không bị giả mạo phía client.
 *  - Implements {@link Comparable}: sắp xếp theo timestamp tăng dần,
 *    tiện cho việc dựng line chart hoặc xếp hạng theo thời gian.
 */
public class BidTransaction extends Entity implements Comparable<BidTransaction> {

    private static final long serialVersionUID = 1L;

    private Long auctionId;
    private Long bidderId;
    private transient Bidder bidder; // optional, không serialize cùng — tránh chu trình
    private double bidAmount;
    private LocalDateTime timestamp;

    /** Constructor rỗng cho serialization / DAO. */
    public BidTransaction() {
        super();
    }

    /**
     * Tạo bid mới khi bidder thực hiện đặt giá. Timestamp = now().
     * Validate amount > 0; bidder không null.
     */
    public BidTransaction(Bidder bidder, double bidAmount) {
        super();
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder không được null");
        }
        if (Double.isNaN(bidAmount) || bidAmount <= 0) {
            throw new IllegalArgumentException(
                    "Số tiền đặt giá phải > 0, nhận: " + bidAmount);
        }
        this.bidder = bidder;
        this.bidderId = bidder.getId();
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public Long getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(Long auctionId) {
        this.auctionId = auctionId;
    }

    public Long getBidderId() {
        return bidderId;
    }

    public void setBidderId(Long bidderId) {
        this.bidderId = bidderId;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
        if (bidder != null) {
            this.bidderId = bidder.getId();
        }
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        if (Double.isNaN(bidAmount) || bidAmount <= 0) {
            throw new IllegalArgumentException(
                    "Số tiền đặt giá phải > 0, nhận: " + bidAmount);
        }
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /** So sánh theo timestamp — tiện cho sort lịch sử bid. */
    @Override
    public int compareTo(BidTransaction other) {
        return Objects.compare(this.timestamp, other.timestamp,
                LocalDateTime::compareTo);
    }

    @Override
    public void displayInfo() {
        System.out.println("[BID] id=" + getId()
                + " | auctionId=" + auctionId
                + " | bidderId=" + bidderId
                + " | amount=$" + bidAmount
                + " | at=" + timestamp);
    }

    @Override
    public String toString() {
        return "BidTransaction{id=" + getId()
                + ", auctionId=" + auctionId
                + ", bidderId=" + bidderId
                + ", amount=" + bidAmount
                + ", time=" + timestamp + '}';
    }
}
