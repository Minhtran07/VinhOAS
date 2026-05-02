package com.bt.shared;

/**
 * Sản phẩm điện tử: điện thoại, laptop, TV...
 * Có thông tin hãng (brand) và bảo hành (warrantyMonths).
 */
public class Electronics extends Item {

    private static final long serialVersionUID = 1L;

    private String brand;
    private int warrantyMonths;

    public Electronics() {
        super();
    }

    public Electronics(String name, String description, double startingPrice,
                       String brand, int warrantyMonths) {
        super(name, description, startingPrice);
        setBrand(brand);
        setWarrantyMonths(warrantyMonths);
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) {
            throw new IllegalArgumentException("Brand không được để trống");
        }
        this.brand = brand.trim();
    }

    /** Raw setter cho DAO. */
    public void setBrandRaw(String brand) { this.brand = brand; }
    public void setWarrantyMonthsRaw(int m) { this.warrantyMonths = m; }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(int warrantyMonths) {
        if (warrantyMonths < 0 || warrantyMonths > 120) {
            throw new IllegalArgumentException(
                    "warrantyMonths phải trong [0, 120], nhận: " + warrantyMonths);
        }
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public ItemCategory getCategory() {
        return ItemCategory.ELECTRONICS;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ Brand: " + brand + " | Warranty: " + warrantyMonths + " tháng");
    }
}
