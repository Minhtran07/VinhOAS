package com.bt.shared.protocol.dto;

public class SubscriptionResponse {

    private long auctionId;
    private boolean subscribed;
    private int totalSubscribers;

    public SubscriptionResponse() { /* Gson */ }

    public SubscriptionResponse(long auctionId, boolean subscribed, int totalSubscribers) {
        this.auctionId = auctionId;
        this.subscribed = subscribed;
        this.totalSubscribers = totalSubscribers;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public boolean isSubscribed() { return subscribed; }
    public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }

    public int getTotalSubscribers() { return totalSubscribers; }
    public void setTotalSubscribers(int totalSubscribers) { this.totalSubscribers = totalSubscribers; }
}
