package com.bt.shared;

/**
 * Người bán: đăng sản phẩm để đấu giá.
 *
 * Đặc thù:
 *  - {@code sellerRating}: điểm đánh giá từ 0.0 đến 5.0.
 *    Có thể dùng để xếp hạng / lọc seller uy tín ở UI.
 */
public class Seller extends User {

    private static final long serialVersionUID = 1L;

    public static final double MIN_RATING = 0.0;
    public static final double MAX_RATING = 5.0;

    private double sellerRating;

    /** Constructor rỗng cho serialization / DAO. */
    public Seller() {
        super();
    }

    public Seller(String username, String email, String password, double initialRating) {
        super(username, email, password);
        setSellerRating(initialRating);
    }

    public double getSellerRating() {
        return sellerRating;
    }

    public void setSellerRating(double sellerRating) {
        if (Double.isNaN(sellerRating)
                || sellerRating < MIN_RATING
                || sellerRating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "Rating phải nằm trong [" + MIN_RATING + ", " + MAX_RATING
                            + "], nhận: " + sellerRating);
        }
        this.sellerRating = sellerRating;
    }

    /** Set không validate — clamp về [0,5]. Dùng cho DAO. */
    public void setSellerRatingRaw(double sellerRating) {
        if (Double.isNaN(sellerRating)) sellerRating = 0;
        this.sellerRating = Math.max(MIN_RATING, Math.min(MAX_RATING, sellerRating));
    }

    @Override
    public UserRole getRole() {
        return UserRole.SELLER;
    }

    @Override
    public void displayInfo() {
        super.displayInfo();
        System.out.println("    └─ Rating: " + sellerRating + "/" + MAX_RATING);
    }
}
