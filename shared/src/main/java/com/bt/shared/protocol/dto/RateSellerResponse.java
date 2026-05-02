package com.bt.shared.protocol.dto;

/** Trả về stars và avg mới của seller sau rating. */
public class RateSellerResponse {

    private long auctionId;
    private long sellerId;
    private int stars;
    private double newAverageRating;
    private int totalRatings;

    public RateSellerResponse() { /* Gson */ }

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getSellerId() { return sellerId; }
    public void setSellerId(long sellerId) { this.sellerId = sellerId; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public double getNewAverageRating() { return newAverageRating; }
    public void setNewAverageRating(double newAverageRating) { this.newAverageRating = newAverageRating; }

    public int getTotalRatings() { return totalRatings; }
    public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }
}
