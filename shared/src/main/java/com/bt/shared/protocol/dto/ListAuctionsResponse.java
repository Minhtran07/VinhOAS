package com.bt.shared.protocol.dto;

import java.util.List;

public class ListAuctionsResponse {

    private List<AuctionDto> auctions;

    public ListAuctionsResponse() { /* Gson */ }

    public ListAuctionsResponse(List<AuctionDto> auctions) {
        this.auctions = auctions;
    }

    public List<AuctionDto> getAuctions() { return auctions; }
    public void setAuctions(List<AuctionDto> auctions) { this.auctions = auctions; }
}
