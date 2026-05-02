package com.bt.shared.exception;

/**
 * Ném ra khi thực hiện hành vi không hợp lệ với trạng thái hiện tại của phiên đấu giá:
 *  - Đặt giá khi phiên đang OPEN (chưa bắt đầu) hoặc đã FINISHED/CANCELED
 *  - Cố start một phiên đã đóng
 *  - Cố cancel một phiên đã PAID
 *
 * Việc tách riêng exception này giúp UI có thể hiện thông báo phù hợp:
 * "Phiên đấu giá đã kết thúc" thay vì lỗi chung chung.
 */
public class AuctionStateException extends AuctionException {

    private static final long serialVersionUID = 1L;

    public AuctionStateException(String message) {
        super(message);
    }
}
