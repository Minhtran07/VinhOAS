package com.bt.shared.protocol.dto;

public class CreateAuctionResponse {

    private AuctionDto auction;

    public CreateAuctionResponse() { /* Gson */ }

    public CreateAuctionResponse(AuctionDto auction) { this.auction = auction; }

    public AuctionDto getAuction() { return auction; }
    public void setAuction(AuctionDto auction) { this.auction = auction; }
}
