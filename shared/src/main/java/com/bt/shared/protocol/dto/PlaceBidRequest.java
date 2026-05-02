package com.bt.shared.protocol.dto;

public class PlaceBidRequest {

    private long auctionId;
    private long bidderId;
    private double amount;

    public PlaceBidRequest() { /* Gson */ }

    public PlaceBidRequest(long auctionId, long bidderId, double amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getBidderId() { return bidderId; }
    public void setBidderId(long bidderId) { this.bidderId = bidderId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
