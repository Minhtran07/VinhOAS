package com.bt.server.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Helper convert datetime giữa Java và DB (SQLite + MySQL).
 *
 * SQLite không có kiểu DATETIME riêng — datetime thường lưu dạng TEXT
 * theo ISO-8601. Tuy nhiên, dữ liệu legacy có thể đến từ nhiều nguồn:
 *  - String ISO "T":   2026-04-26T14:30:15
 *  - String space:     2026-04-26 14:30:15
 *  - Số millis (long): 1745669415000  (do JDBC setTimestamp version cũ)
 *  - Có nanos:         2026-04-26T14:30:15.123
 *  - Thiếu giây:       2026-04-26T14:30
 *
 * Hàm {@link #getLocalDateTime} thử lần lượt mọi format để đảm bảo
 * dữ liệu legacy không bị mất khi đọc lại bằng code mới.
 *
 * Khi WRITE, ta chuẩn hóa về 1 format duy nhất (ISO_LOCAL_DATE_TIME)
 * để lần đọc tiếp theo đơn giản.
 */
public final class SqlTime {

    private static final DateTimeFormatter WRITE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Các format để thử khi đọc — thứ tự từ chuẩn nhất tới lạ nhất. */
    private static final DateTimeFormatter[] READ_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,                       // 2026-04-26T14:30:15
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),          // 2026-04-26 14:30:15
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),      // có ms
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),    // ISO + ms
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),             // thiếu giây
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
    };

    private SqlTime() {}

    public static void setLocalDateTime(PreparedStatement ps, int idx,
                                        LocalDateTime value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.VARCHAR);
        else ps.setString(idx, value.format(WRITE_FORMAT));
    }

    /**
     * Đọc cột datetime, tolerant với nhiều format.
     * Trả null nếu cột NULL hoặc không parse được (đã log).
     */
    public static LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
        // Bước 1: thử đọc dạng String
        String s = rs.getString(column);
        if (s == null || s.isEmpty()) return null;

        // Trim subsecond nếu có và 6+ digit
        s = s.trim();

        // Bước 2: nếu là số (millis epoch), convert
        if (s.matches("^-?\\d{10,}$")) {
            try {
                long millis = Long.parseLong(s);
                return Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (NumberFormatException ignored) { /* fall through */ }
        }

        // Bước 3: thử các format ISO/space
        for (DateTimeFormatter f : READ_FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignored) { /* try next */ }
        }

        // Bước 4: fallback dùng JDBC Timestamp (driver-specific)
        try {
            Timestamp ts = rs.getTimestamp(column);
            if (ts != null) return ts.toLocalDateTime();
        } catch (SQLException ignored) { /* fall through */ }

        System.err.println("[SqlTime] Không parse được datetime cột "
                + column + ": '" + s + "' — trả null");
        return null;
    }
}
