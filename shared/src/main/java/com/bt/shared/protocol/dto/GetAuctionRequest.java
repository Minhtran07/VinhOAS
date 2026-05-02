package com.bt.shared.protocol.dto;

public class GetAuctionRequest {

    private long auctionId;

    public GetAuctionRequest() { /* Gson */ }

    public GetAuctionRequest(long auctionId) { this.auctionId = auctionId; }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
}
