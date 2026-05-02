package com.bt.shared.exception;

/**
 * Ném ra khi dữ liệu đầu vào không hợp lệ:
 *  - Username/email rỗng hoặc sai định dạng
 *  - Giá khởi điểm âm
 *  - Năm sản xuất không hợp lệ
 *  - Mileage âm,...
 *
 * Là checked exception để bắt buộc tầng caller phải xử lý
 * (hiển thị lỗi cho người dùng / trả về client).
 */
public class ValidationException extends AuctionException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
