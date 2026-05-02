package com.bt.shared.exception;

/**
 * Ném ra khi xác thực thất bại:
 *  - Sai username/password
 *  - Tài khoản bị khóa / vô hiệu hóa
 *  - Token hết hạn (cho mở rộng sau này)
 *
 * Tách riêng để client có thể phân biệt giữa "lỗi đăng nhập" và "lỗi nghiệp vụ".
 */
public class AuthenticationException extends AuctionException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
