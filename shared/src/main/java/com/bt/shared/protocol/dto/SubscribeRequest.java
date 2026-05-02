package com.bt.shared.protocol.dto;

/** Yêu cầu subscribe / unsubscribe sự kiện realtime của 1 phiên đấu giá. */
public class SubscribeRequest {

    private long auctionId;

    public SubscribeRequest() { /* Gson */ }

    public SubscribeRequest(long auctionId) { this.auctionId = auctionId; }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
}
