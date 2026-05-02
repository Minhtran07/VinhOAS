package com.bt.server.dao;

import java.time.LocalDateTime;

/**
 * Kết quả của thao tác persist bid an toàn concurrency.
 *
 * Trả về snapshot sau khi transaction commit để service không phải query lại.
 */
public class BidPersistenceResult {

    private final long bidId;
    private final double newCurrentPrice;
    private final LocalDateTime newEndTime;

    public BidPersistenceResult(long bidId, double newCurrentPrice, LocalDateTime newEndTime) {
        this.bidId = bidId;
        this.newCurrentPrice = newCurrentPrice;
        this.newEndTime = newEndTime;
    }

    public long getBidId() { return bidId; }
    public double getNewCurrentPrice() { return newCurrentPrice; }
    public LocalDateTime getNewEndTime() { return newEndTime; }
}
