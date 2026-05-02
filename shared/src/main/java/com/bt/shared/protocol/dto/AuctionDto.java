package com.bt.shared.protocol.dto;

import com.bt.shared.Auction.AuctionStatus;
import com.bt.shared.ItemCategory;

import java.time.LocalDateTime;

/**
 * View-model gửi cho client khi list/get auction.
 *
 * Tách DTO khỏi entity vì entity chứa các field không cần (transient seller
 * object, version field, list bid lớn,...). DTO chỉ giữ thông tin UI cần
 * để giảm payload và tránh leak.
 */
public class AuctionDto {

    private long auctionId;
    private long itemId;
    private String itemName;
    private String itemDescription;
    private ItemCategory itemCategory;
    private double startingPrice;
    private double currentPrice;
    private long sellerId;
    private String sellerUsername;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private int bidCount;
    private Long winnerBidderId;
    private String winnerUsername;

    public AuctionDto() { /* Gson */ }

    // Getters / Setters

    public long getAuctionId() { return auctionId; }
    public void setAuctionId(long auctionId) { this.auctionId = auctionId; }

    public long getItemId() { return itemId; }
    public void setItemId(long itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    public ItemCategory getItemCategory() { return itemCategory; }
    public void setItemCategory(ItemCategory itemCategory) { this.itemCategory = itemCategory; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public long getSellerId() { return sellerId; }
    public void setSellerId(long sellerId) { this.sellerId = sellerId; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public int getBidCount() { return bidCount; }
    public void setBidCount(int bidCount) { this.bidCount = bidCount; }

    public Long getWinnerBidderId() { return winnerBidderId; }
    public void setWinnerBidderId(Long winnerBidderId) { this.winnerBidderId = winnerBidderId; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }
}
