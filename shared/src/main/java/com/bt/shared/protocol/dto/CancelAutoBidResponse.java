package com.bt.shared.protocol.dto;

public class CancelAutoBidResponse {

    private long auctionId;
    private boolean canceled;

    public CancelAutoBidResponse() { /* Gson */ }

    public CancelAutoBidResponse(long auctionId, boolean canceled) {
        this.auctionId = auctionId;
        this.canceled = canceled;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
}
