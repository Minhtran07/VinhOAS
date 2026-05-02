package com.bt.shared.protocol.dto;

import com.bt.shared.ItemCategory;

/**
 * View-model item gửi cho client. Chứa cả các field role-specific
 * (brand, artist, make...) để Seller xem được trong "Sản phẩm của tôi".
 */
public class ItemDto {

    private long itemId;
    private String name;
    private String description;
    private double startingPrice;
    private ItemCategory category;
    private long sellerId;
    private String sellerUsername;

    // Cột phụ — chỉ một nhóm có giá trị tùy category
    private String brand;
    private Integer warrantyMonths;
    private String artist;
    private Integer yearCreated;
    private String make;
    private String model;
    private Integer mileage;

    /** Có phiên đấu giá đang chạy/đã đăng cho item này không. */
    private boolean hasActiveAuction;

    public ItemDto() { /* Gson */ }

    public long getItemId() { return itemId; }
    public void setItemId(long itemId) { this.itemId = itemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public long getSellerId() { return sellerId; }
    public void setSellerId(long sellerId) { this.sellerId = sellerId; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public Integer getYearCreated() { return yearCreated; }
    public void setYearCreated(Integer yearCreated) { this.yearCreated = yearCreated; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getMileage() { return mileage; }
    public void setMileage(Integer mileage) { this.mileage = mileage; }

    public boolean isHasActiveAuction() { return hasActiveAuction; }
    public void setHasActiveAuction(boolean hasActiveAuction) { this.hasActiveAuction = hasActiveAuction; }
}
