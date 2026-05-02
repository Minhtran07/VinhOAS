package com.bt.shared.protocol.dto;

import com.bt.shared.Auction.AuctionStatus;

/**
 * Filter list phiên đấu giá. Mọi field đều optional.
 */
public class ListAuctionsRequest {

    private AuctionStatus statusFilter; // null = tất cả

    public ListAuctionsRequest() { /* Gson */ }

    public ListAuctionsRequest(AuctionStatus statusFilter) {
        this.statusFilter = statusFilter;
    }

    public AuctionStatus getStatusFilter() { return statusFilter; }
    public void setStatusFilter(AuctionStatus statusFilter) { this.statusFilter = statusFilter; }
}
