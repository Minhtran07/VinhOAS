package com.bt.shared.protocol.dto;

import java.util.List;

public class GetBidHistoryResponse {

    private long auctionId;
    private List<BidDto> bids;

    public GetBidHistoryResponse() { /* Gson */ }

    public GetBidHistoryResponse(long auctionId, List<BidDto> bids) {
        this.auctionId = auctionId;
        this.bids = bids;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public List<BidDto> getBids() { return bids; }
    public void setBids(List<BidDto> bids) { this.bids = bids; }
}
