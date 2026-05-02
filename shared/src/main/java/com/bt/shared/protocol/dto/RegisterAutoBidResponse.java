package com.bt.shared.protocol.dto;

/** Server confirm đã đăng ký auto-bid. */
public class RegisterAutoBidResponse {

    private long configId;
    private long auctionId;
    private long bidderId;
    private double maxBid;
    private double increment;
    private boolean active;

    public RegisterAutoBidResponse() { /* Gson */ }

    public long getConfigId() { return configId; }
    public void setConfigId(long configId) { this.configId = configId; }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getBidderId() { return bidderId; }
    public void setBidderId(long bidderId) { this.bidderId = bidderId; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getIncrement() { return increment; }
    public void setIncrement(double increment) { this.increment = increment; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
