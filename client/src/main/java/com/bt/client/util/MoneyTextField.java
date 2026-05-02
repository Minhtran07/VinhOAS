package com.bt.client.util;

import javafx.scene.control.TextField;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Gắn auto-format vào TextField cho nhập số tiền.
 *
 * Behavior:
 *  - Mỗi khi user gõ, text tự được format với dấu phẩy mỗi 3 chữ số.
 *    Ví dụ: gõ "1500000" → hiển thị "1,500,000".
 *  - Chỉ chấp nhận chữ số (0-9). Mọi ký tự khác bị strip.
 *  - {@link #parseValue(TextField)} trả về giá trị số thuần (long).
 *
 * Thuật toán giữ caret:
 *  Đếm số chữ số ở vị trí caret cũ → format lại → tìm vị trí mới có cùng
 *  số chữ số đứng trước. Nhờ vậy caret không nhảy lung tung khi insert phẩy.
 */
public final class MoneyTextField {

    private static final DecimalFormat FMT;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator(',');
        FMT = new DecimalFormat("#,###", sym);
    }

    private MoneyTextField() {}

    /** Cài listener vào field. Idempotent — gọi nhiều lần OK. */
    public static void install(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            // Chỉ giữ chữ số
            String digits = newVal.replaceAll("[^0-9]", "");
            // Bỏ leading zero (trừ trường hợp chuỗi rỗng / chỉ "0")
            digits = digits.replaceFirst("^0+(?!$)", "");

            String formatted = digits.isEmpty() ? "" : FMT.format(Long.parseLong(digits));
            if (formatted.equals(newVal)) return;

            // Đếm chữ số trước caret cũ để khôi phục vị trí
            int caretOld = field.getCaretPosition();
            int digitsBeforeCaret = countDigits(newVal, caretOld);

            field.setText(formatted);

            // Tìm vị trí mới: index thứ digitsBeforeCaret chữ số trong chuỗi đã format
            int newCaret = positionAfterNDigits(formatted, digitsBeforeCaret);
            field.positionCaret(newCaret);
        });
    }

    /** Trả số tiền thuần. Trả 0 nếu rỗng. */
    public static long parseValue(TextField field) {
        if (field.getText() == null) return 0;
        String d = field.getText().replaceAll("[^0-9]", "");
        return d.isEmpty() ? 0 : Long.parseLong(d);
    }

    private static int countDigits(String s, int upTo) {
        int n = 0;
        int limit = Math.min(upTo, s.length());
        for (int i = 0; i < limit; i++) {
            if (Character.isDigit(s.charAt(i))) n++;
        }
        return n;
    }

    private static int positionAfterNDigits(String s, int n) {
        if (n <= 0) return 0;
        int seen = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                seen++;
                if (seen == n) return i + 1;
            }
        }
        return s.length();
    }
}
