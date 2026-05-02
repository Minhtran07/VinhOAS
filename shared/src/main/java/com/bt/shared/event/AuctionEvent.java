package com.bt.shared.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Sự kiện cơ sở của hệ thống đấu giá.
 *
 * Mỗi event mang theo {@code auctionId} để subscriber biết phiên nào bị
 * ảnh hưởng + {@code timestamp} ghi nhận thời điểm phát sinh.
 *
 * Class này abstract để bắt subclass khai báo loại event cụ thể (BidPlaced,
 * AuctionFinished, AuctionExtended,...) — nhờ vậy Observer có thể switch
 * theo class và compiler sẽ cảnh báo nếu thêm loại mới mà quên handle.
 */
public abstract class AuctionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long auctionId;
    private final LocalDateTime timestamp;

    protected AuctionEvent(long auctionId) {
        this.auctionId = auctionId;
        this.timestamp = LocalDateTime.now();
    }

    public long getAuctionId() { return auctionId; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
