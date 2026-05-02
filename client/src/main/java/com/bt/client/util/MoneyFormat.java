package com.bt.client.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Format tiền VND nhất quán toàn ứng dụng.
 *
 * Quy ước hiển thị:
 *   1.234.567 ₫    (dấu phẩy/chấm theo locale, hậu tố ₫)
 *
 * Lý do dùng class chung thay vì NumberFormat.getCurrencyInstance(Locale.VN):
 *  - JavaFX text rendering ở macOS đôi khi parse symbol "₫" sai
 *  - Custom format cho phép kiểm soát chính xác dấu phân tách hàng nghìn
 *    (dùng dấu phẩy theo yêu cầu)
 */
public final class MoneyFormat {

    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        FORMATTER = new DecimalFormat("#,###", symbols);
    }

    private MoneyFormat() {}

    /** Format số sang chuỗi VND (vd: "1,234,567 ₫"). */
    public static String vnd(double amount) {
        return FORMATTER.format(Math.round(amount)) + " ₫";
    }

    /** Format không có hậu tố — dùng cho input prefill. */
    public static String number(double amount) {
        return FORMATTER.format(Math.round(amount));
    }
}
