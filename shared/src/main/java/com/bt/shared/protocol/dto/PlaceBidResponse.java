package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

public class PlaceBidResponse {

    private long bidId;
    private long auctionId;
    private double newCurrentPrice;
    private LocalDateTime newEndTime;   // có thể đã được kéo dài bởi anti-sniping
    private LocalDateTime bidTime;

    public PlaceBidResponse() { /* Gson */ }

    public long getBidId() { return bidId; }
    public void setBidId(long bidId) { this.bidId = bidId; }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public double getNewCurrentPrice() { return newCurrentPrice; }
    public void setNewCurrentPrice(double newCurrentPrice) { this.newCurrentPrice = newCurrentPrice; }

    public LocalDateTime getNewEndTime() { return newEndTime; }
    public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }

    public LocalDateTime getBidTime() { return bidTime; }
    public void setBidTime(LocalDateTime bidTime) { this.bidTime = bidTime; }
}
