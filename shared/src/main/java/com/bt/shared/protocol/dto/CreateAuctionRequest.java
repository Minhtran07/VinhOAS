package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

/**
 * Tạo phiên đấu giá từ một item đã tồn tại (của chính seller đang đăng nhập).
 *
 * Server sẽ:
 *  - Verify role SELLER
 *  - Verify item.sellerId = session.userId
 *  - Verify item chưa có phiên active
 *  - Validate startTime < endTime, startTime >= now (cho phép nhỏ hơn vài phút)
 *  - Auto-promote sang RUNNING nếu startTime <= now (qua scheduler)
 */
public class CreateAuctionRequest {

    private long itemId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public CreateAuctionRequest() { /* Gson */ }

    public CreateAuctionRequest(long itemId, LocalDateTime startTime, LocalDateTime endTime) {
        this.itemId = itemId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public long getItemId() { return itemId; }
    public void setItemId(long itemId) { this.itemId = itemId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
