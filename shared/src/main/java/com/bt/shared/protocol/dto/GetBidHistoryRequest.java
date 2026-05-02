package com.bt.shared.protocol.dto;

public class GetBidHistoryRequest {

    private long auctionId;

    public GetBidHistoryRequest() { /* Gson */ }

    public GetBidHistoryRequest(long auctionId) { this.auctionId = auctionId; }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
}
