package com.bt.shared.exception;

/**
 * Ném ra khi một lệnh đặt giá (bid) vi phạm quy tắc:
 *  - Bid thấp hơn hoặc bằng giá hiện tại
 *  - Bid nhỏ hơn giá khởi điểm
 *  - Bidder tự đấu giá sản phẩm của chính mình (nếu bật rule này)
 *  - Số tiền đặt giá <= 0 hoặc NaN
 */
public class InvalidBidException extends AuctionException {

    private static final long serialVersionUID = 1L;

    public InvalidBidException(String message) {
        super(message);
    }
}
