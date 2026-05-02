package com.bt.shared;

/**
 * Lớp trừu tượng đại diện cho một sản phẩm được đem đấu giá.
 *
 * Phân cấp: {@code Item ─▶ Electronics / Art / Vehicle}
 *
 * Đặc tính chung của mọi item:
 *  - tên, mô tả, giá khởi điểm (startingPrice)
 *  - sellerId: liên kết tới {@link Seller} đăng sản phẩm. Lưu id thay vì cả
 *    object để tránh aggregate cycle khi serialize, đồng thời khớp với
 *    cách lưu trong DB (foreign key).
 *
 * Validation: startingPrice phải > 0; tên không được rỗng.
 */
public abstract class Item extends Entity {

    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
    private double startingPrice;
    private Long sellerId;

    /** Constructor rỗng cho serialization / DAO. */
    protected Item() {
        super();
    }

    protected Item(String name, String description, double startingPrice) {
        super();
        setName(name);
        setDescription(description);
        setStartingPrice(startingPrice);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("Tên sản phẩm không quá 200 ký tự");
        }
        this.name = name.trim();
    }

    /** Set không validate — chỉ dùng cho DAO load. */
    public void setNameRaw(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        // Description có thể rỗng — sản phẩm chưa cần mô tả vẫn đăng được
        this.description = description == null ? "" : description.trim();
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        if (Double.isNaN(startingPrice) || startingPrice <= 0) {
            throw new IllegalArgumentException(
                    "Giá khởi điểm phải > 0, nhận: " + startingPrice);
        }
        this.startingPrice = startingPrice;
    }

    /** Set không validate — DAO load. Clamp âm về 0. */
    public void setStartingPriceRaw(double startingPrice) {
        if (Double.isNaN(startingPrice) || startingPrice < 0) startingPrice = 0;
        this.startingPrice = startingPrice;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    /**
     * Mỗi loại item phải khai báo {@link ItemCategory} của mình.
     * Phương thức này dùng cho Factory, lưu DB và filter UI.
     */
    public abstract ItemCategory getCategory();

    @Override
    public void displayInfo() {
        System.out.println("[" + getCategory() + "] id=" + getId()
                + " | name=" + name
                + " | startingPrice=$" + startingPrice
                + (sellerId != null ? " | sellerId=" + sellerId : ""));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{id=" + getId()
                + ", name='" + name + '\''
                + ", startingPrice=" + startingPrice
                + ", category=" + getCategory()
                + '}';
    }
}
