package com.bt.shared.protocol;

/**
 * Mã lỗi chuẩn hóa để client phân biệt loại lỗi mà không cần parse message.
 *
 * Tách enum riêng giúp:
 *  - I18n: client tự dịch theo code thay vì hiển thị raw message từ server
 *  - Logic: client có thể decide retry / show form / logout dựa trên code
 */
public enum ErrorCode {
    /** Sai username hoặc password. */
    AUTH_FAILED,
    /** Tài khoản bị vô hiệu hóa. */
    AUTH_DISABLED,
    /** Username/email đã tồn tại. */
    REGISTER_DUPLICATE,
    /** Dữ liệu đầu vào không hợp lệ. */
    VALIDATION_FAILED,
    /** Bid sai quy tắc. */
    INVALID_BID,
    /** Phiên đấu giá không ở trạng thái cho phép thao tác này. */
    AUCTION_STATE_INVALID,
    /** Không tìm thấy resource. */
    NOT_FOUND,
    /** Không có quyền. */
    FORBIDDEN,
    /** Lỗi nội bộ server (DB, IO,...). */
    INTERNAL_ERROR,
    /** Request type chưa được hỗ trợ. */
    UNSUPPORTED_TYPE,
    /** Payload sai format / parse fail. */
    BAD_REQUEST
}
