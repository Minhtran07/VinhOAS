package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

/** Một bản ghi bid trong lịch sử / chart. */
public class BidDto {

    private long bidId;
    private long auctionId;
    private long bidderId;
    private String bidderUsername;
    private double amount;
    private LocalDateTime bidTime;
    private boolean autoBid;

    public BidDto() { /* Gson */ }

    public long getBidId() { return bidId; }
    public void setBidId(long bidId) { this.bidId = bidId; }

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

    public boolean isAutoBid() { return autoBid; }
    public void setAutoBid(boolean autoBid) { this.autoBid = autoBid; }
}
