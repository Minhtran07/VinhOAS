package com.bt.shared.exception;

/**
 * Base exception cho toàn bộ domain của hệ thống đấu giá.
 * Các exception cụ thể (Validation, InvalidBid, AuctionState, Authentication...)
 * đều kế thừa class này để client/server có thể bắt chung khi cần.
 *
 * Đây là một checked exception nhằm bắt buộc lớp gọi xử lý lỗi nghiệp vụ
 * (đặt giá sai, phiên đã đóng, đăng nhập sai...) một cách tường minh.
 */
public class AuctionException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuctionException(String message) {
        super(message);
    }

    public AuctionException(String message, Throwable cause) {
        super(message, cause);
    }
}
