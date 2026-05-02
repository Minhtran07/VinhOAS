package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

/** Server push khi có bid mới hợp lệ trên một phiên có subscriber. */
public class BidPlacedEvent {

    private long auctionId;
    private long bidderId;
    private String bidderUsername;
    private double amount;
    private LocalDateTime bidTime;
    private LocalDateTime newEndTime; // có thể đã được kéo dài

    public BidPlacedEvent() { /* Gson */ }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getBidderId() { return bidderId; }
    public void setBidderId(long bidderId) { this.bidderId = bidderId; }

    public String getBidderUsername() { return bidderUsername; }
    public void setBidderUsername(String bidderUsername) { this.bidderUsername = bidderUsername; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getBidTime() { return bidTime; }
    public void setBidTime(LocalDateTime bidTime) { this.bidTime = bidTime; }

    public LocalDateTime getNewEndTime() { return newEndTime; }
    public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }
}
