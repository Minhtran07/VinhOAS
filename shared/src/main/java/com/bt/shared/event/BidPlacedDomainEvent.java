package com.bt.shared.event;

import java.time.LocalDateTime;

/**
 * Phát ra khi một bid hợp lệ được đặt.
 *
 * Tách khỏi {@code BidPlacedEvent} ở package {@code protocol.dto} (DTO cho
 * wire) để giữ event domain "thuần Java" — observer ở server có thể dùng
 * trực tiếp mà không bị phụ thuộc vào tầng protocol.
 */
public class BidPlacedDomainEvent extends AuctionEvent {

    private static final long serialVersionUID = 1L;

    private final long bidderId;
    private final String bidderUsername;
    private final double amount;
    private final LocalDateTime newEndTime;

    public BidPlacedDomainEvent(long auctionId, long bidderId, String bidderUsername,
                                double amount, LocalDateTime newEndTime) {
        super(auctionId);
        this.bidderId = bidderId;
        this.bidderUsername = bidderUsername;
        this.amount = amount;
        this.newEndTime = newEndTime;
    }

    public long getBidderId() { return bidderId; }
    public String getBidderUsername() { return bidderUsername; }
    public double getAmount() { return amount; }
    public LocalDateTime getNewEndTime() { return newEndTime; }
}
