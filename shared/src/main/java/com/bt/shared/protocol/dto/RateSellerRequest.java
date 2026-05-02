package com.bt.shared.protocol.dto;

/**
 * Winner đã thanh toán đánh giá seller. Server validate:
 *  - Phiên đã PAID, bidder = winner
 *  - Chưa rate trước đó (UNIQUE constraint)
 *  - stars 1..5
 */
public class RateSellerRequest {

    private long auctionId;
    private int stars;
    private String comment;

    public RateSellerRequest() { /* Gson */ }

    public RateSellerRequest(long auctionId, int stars, String comment) {
        this.auctionId = auctionId;
        this.stars = stars;
        this.comment = comment;
    }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
