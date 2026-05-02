package com.bt.shared.protocol.dto;

/** Push khi phiên đấu giá chuyển sang FINISHED (hết giờ hoặc bị finish thủ công). */
public class AuctionFinishedEvent {

    private long auctionId;
    private Long winnerBidderId;
    private String winnerUsername;
    private double finalPrice;

    public AuctionFinishedEvent() { /* Gson */ }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public Long getWinnerBidderId() { return winnerBidderId; }
    public void setWinnerBidderId(Long winnerBidderId) { this.winnerBidderId = winnerBidderId; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    public double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
}
