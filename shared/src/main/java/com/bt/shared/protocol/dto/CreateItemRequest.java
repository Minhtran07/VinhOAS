package com.bt.shared.protocol.dto;

import com.bt.shared.ItemCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * Yêu cầu tạo item mới. Server sẽ validate role là SELLER và set
 * sellerId từ session, không dùng field từ client (chống forge).
 *
 * spec chứa các field tùy category (brand, artist, ...). Server dùng
 * {@code ItemFactory.create()} để build entity.
 */
public class CreateItemRequest {

    private String name;
    private String description;
    private double startingPrice;
    private ItemCategory category;
    private Map<String, Object> spec = new HashMap<>();

    public CreateItemRequest() { /* Gson */ }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public Map<String, Object> getSpec() { return spec; }
    public void setSpec(Map<String, Object> spec) { this.spec = spec; }
}
