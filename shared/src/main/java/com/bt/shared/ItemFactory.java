package com.bt.shared;

import com.bt.shared.exception.ValidationException;

import java.util.Map;

/**
 * Factory Method để tạo {@link Item} dựa trên category + tham số riêng
 * cho từng loại.
 *
 * Thiết kế:
 *  - Dùng {@link ItemCategory} (enum) thay vì String — compile-time safe.
 *  - Tham số riêng (brand, artist, mileage...) được truyền qua {@code Map<String,Object>}
 *    thay vì {@code String...}: tự gán theo tên key, không bị nhầm thứ tự,
 *    và linh hoạt khi thêm trường mới.
 *  - Các key được khai báo public static để các tầng khác (controller, UI form)
 *    dùng chung — tránh hard-code chuỗi.
 *
 * Ví dụ tạo Electronics:
 * <pre>
 *   Map<String, Object> spec = Map.of(
 *       ItemFactory.KEY_BRAND, "Apple",
 *       ItemFactory.KEY_WARRANTY_MONTHS, 12);
 *   Item it = ItemFactory.create(ItemCategory.ELECTRONICS,
 *                                "iPhone 15", "Like new", 1200.0, spec);
 * </pre>
 */
public final class ItemFactory {

    // Keys cho Electronics
    public static final String KEY_BRAND = "brand";
    public static final String KEY_WARRANTY_MONTHS = "warrantyMonths";

    // Keys cho Art
    public static final String KEY_ARTIST = "artist";
    public static final String KEY_YEAR_CREATED = "yearCreated";

    // Keys cho Vehicle
    public static final String KEY_MAKE = "make";
    public static final String KEY_MODEL = "model";
    public static final String KEY_MILEAGE = "mileage";

    private ItemFactory() {
        // Utility class — chặn khởi tạo
    }

    /**
     * Tạo một {@link Item} từ category và tham số chi tiết.
     *
     * @param category      loại sản phẩm (không null)
     * @param name          tên sản phẩm
     * @param description   mô tả (có thể rỗng)
     * @param startingPrice giá khởi điểm (>0)
     * @param spec          map chứa tham số riêng theo từng category
     * @throws ValidationException nếu tham số thiếu hoặc sai kiểu
     */
    public static Item create(ItemCategory category,
                              String name,
                              String description,
                              double startingPrice,
                              Map<String, Object> spec) throws ValidationException {
        if (category == null) {
            throw new ValidationException("Category không được null");
        }
        if (spec == null) {
            throw new ValidationException("Spec không được null");
        }
        try {
            switch (category) {
                case ELECTRONICS:
                    return new Electronics(
                            name, description, startingPrice,
                            requireString(spec, KEY_BRAND),
                            requireInt(spec, KEY_WARRANTY_MONTHS));
                case ART:
                    return new Art(
                            name, description, startingPrice,
                            requireString(spec, KEY_ARTIST),
                            requireInt(spec, KEY_YEAR_CREATED));
                case VEHICLE:
                    return new Vehicle(
                            name, description, startingPrice,
                            requireString(spec, KEY_MAKE),
                            requireString(spec, KEY_MODEL),
                            requireInt(spec, KEY_MILEAGE));
                default:
                    // Compiler sẽ báo nếu thêm category mới mà quên xử lý
                    throw new ValidationException("Category chưa được hỗ trợ: " + category);
            }
        } catch (IllegalArgumentException ex) {
            // Validate trong constructor của subclass throw IllegalArgumentException
            // — bọc lại thành ValidationException để tầng caller xử lý.
            throw new ValidationException(ex.getMessage(), ex);
        }
    }

    /**
     * Overload nhận String để tiện gọi từ form UI / network — convert sang enum.
     */
    public static Item create(String categoryRaw,
                              String name,
                              String description,
                              double startingPrice,
                              Map<String, Object> spec) throws ValidationException {
        ItemCategory cat = ItemCategory.fromString(categoryRaw);
        if (cat == null) {
            throw new ValidationException("Category không hợp lệ: " + categoryRaw);
        }
        return create(cat, name, description, startingPrice, spec);
    }

    private static String requireString(Map<String, Object> spec, String key)
            throws ValidationException {
        Object v = spec.get(key);
        if (v == null) {
            throw new ValidationException("Thiếu trường bắt buộc: " + key);
        }
        return v.toString();
    }

    private static int requireInt(Map<String, Object> spec, String key)
            throws ValidationException {
        Object v = spec.get(key);
        if (v == null) {
            throw new ValidationException("Thiếu trường bắt buộc: " + key);
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(
                    "Trường " + key + " phải là số nguyên, nhận: " + v);
        }
    }
}
