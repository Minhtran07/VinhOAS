package com.bt.shared;

/**
 * Danh mục sản phẩm đấu giá.
 * Dùng enum để Factory không phụ thuộc vào String và compiler có thể bảo vệ
 * khi thêm category mới.
 */
public enum ItemCategory {
    ELECTRONICS,
    ART,
    VEHICLE;

    public static ItemCategory fromString(String raw) {
        if (raw == null) return null;
        try {
            return ItemCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
