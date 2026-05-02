package com.bt.shared.protocol.dto;

/**
 * Bidder thắng phiên gửi request thanh toán. Server sẽ:
 *  - Validate bidder = winner_bidder_id, status = FINISHED
 *  - Trừ balance bidder, set status PAID, ghi paid_at + paid_amount
 *  - Push AUCTION_PAID_EVENT cho seller (và bidder)
 */
public class PayAuctionRequest {

    private long auctionId;

    public PayAuctionRequest() { /* Gson */ }

    public PayAuctionRequest(long auctionId) {
        this.auctionId = auctionId;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
}
