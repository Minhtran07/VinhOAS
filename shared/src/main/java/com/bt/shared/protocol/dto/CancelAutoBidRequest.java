package com.bt.shared.protocol.dto;

/** Hủy auto-bid cho 1 phiên. Server tự lấy bidderId từ session. */
public class CancelAutoBidRequest {

    private long auctionId;

    public CancelAutoBidRequest() { /* Gson */ }

    public CancelAutoBidRequest(long auctionId) {
        this.auctionId = auctionId;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
}
