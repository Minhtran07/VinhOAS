package com.bt.client.net;

import com.bt.shared.protocol.ErrorCode;

/**
 * Lỗi do server trả về (ERROR_RESPONSE) hoặc lỗi IO/timeout từ phía client.
 *
 * UI controller bắt exception này, dùng {@link #getCode()} để chọn alert
 * type / message phù hợp.
 */
public class AuctionClientException extends Exception {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public AuctionClientException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AuctionClientException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
