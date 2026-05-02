package com.bt.client.util;

import com.bt.shared.protocol.ErrorCode;

/**
 * Map từ ErrorCode kỹ thuật → tiêu đề tiếng Việt thân thiện cho user.
 *
 * Tách thành class riêng để có thể mở rộng (vd: i18n) sau này mà không
 * phải sửa nhiều controller.
 */
public final class ErrorMessages {

    private ErrorMessages() {}

    public static String title(ErrorCode code) {
        if (code == null) return "Có lỗi xảy ra";
        switch (code) {
            case AUTH_FAILED:           return "Đăng nhập thất bại";
            case AUTH_DISABLED:         return "Tài khoản bị khóa";
            case REGISTER_DUPLICATE:    return "Tài khoản đã tồn tại";
            case VALIDATION_FAILED:     return "Dữ liệu không hợp lệ";
            case INVALID_BID:           return "Đặt giá không hợp lệ";
            case AUCTION_STATE_INVALID: return "Phiên đấu giá không cho phép thao tác này";
            case NOT_FOUND:             return "Không tìm thấy";
            case FORBIDDEN:             return "Không có quyền";
            case INTERNAL_ERROR:        return "Lỗi hệ thống";
            case UNSUPPORTED_TYPE:      return "Yêu cầu không được hỗ trợ";
            case BAD_REQUEST:           return "Yêu cầu sai định dạng";
            default:                    return "Có lỗi xảy ra";
        }
    }
}
