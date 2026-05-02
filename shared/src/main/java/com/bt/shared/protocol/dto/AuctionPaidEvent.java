package com.bt.shared.protocol.dto;

import java.time.LocalDateTime;

/** Server push khi 1 phiên đã được winner thanh toán. */
public class AuctionPaidEvent {

    private long auctionId;
    private long winnerBidderId;
    private String winnerUsername;
    private double paidAmount;
    private LocalDateTime paidAt;

    public AuctionPaidEvent() { /* Gson */ }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getWinnerBidderId() { return winnerBidderId; }
    public void setWinnerBidderId(long winnerBidderId) { this.winnerBidderId = winnerBidderId; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
