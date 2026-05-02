package com.bt.shared.protocol;

/**
 * Lỗi liên quan đến giao thức: JSON sai cú pháp, message thiếu trường,
 * payload không khớp DTO,...
 *
 * Là {@link RuntimeException} (unchecked) vì caller thường ở trong
 * Listener thread không phù hợp để bắt nhiều exception.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
