package com.bt.server.dao;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Khởi tạo / migrate schema lần mỗi lần server start.
 *
 * Nguyên tắc THIẾT YẾU:
 *  - **KHÔNG BAO GIỜ** drop bảng hay xóa dữ liệu của user.
 *  - {@code schema.sql} dùng CREATE ... IF NOT EXISTS — idempotent, không
 *    phá data đã có.
 *  - {@code seed.sql} chỉ chạy khi bảng users đang trống (lần đầu tạo DB).
 *  - Khi schema thay đổi, thêm cột mới qua hàm {@code addColumnIfMissing}
 *    bên dưới — tự skip nếu cột đã tồn tại.
 *
 * Nhờ vậy, dù dev sửa code và restart server N lần, dữ liệu user, item,
 * auction, bid đã có sẽ được bảo toàn.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {}

    public static void ensureSchema(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            // 1. Apply schema (idempotent CREATE IF NOT EXISTS)
            String schema = loadResource("/db/schema.sql");
            executeScript(c, schema, "schema");

            // 2. Apply migrations cho schema cũ — thêm cột mới nếu chưa có
            applyMigrations(c);

            // 3. Seed CHỈ nếu DB trống
            if (isUsersEmpty(c)) {
                System.out.println("[Schema] DB trống — load seed data");
                String seed = loadResource("/db/seed.sql");
                executeScript(c, seed, "seed");
            } else {
                System.out.println("[Schema] DB đã có dữ liệu — bỏ qua seed");
            }
            System.out.println("[Schema] OK");
        } catch (Exception e) {
            System.err.println("[Schema] FAIL: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("Không khởi tạo được schema: " + e.getMessage(), e);
        }
    }

    /**
     * Apply các migration thêm cột — chạy mọi lần start, idempotent.
     * Khi schema thay đổi, thêm dòng addColumnIfMissing(...) ở đây.
     */
    private static void applyMigrations(Connection c) throws SQLException {
        // Payment: thêm thời điểm và số tiền đã thanh toán cho phiên thắng.
        // Khi auction chuyển từ FINISHED → PAID, các cột này được set.
        addColumnIfMissing(c, "auctions", "paid_at", "TEXT");
        addColumnIfMissing(c, "auctions", "paid_amount", "REAL");
    }

    /**
     * Thêm cột nếu chưa tồn tại. SQLite không có "ADD COLUMN IF NOT EXISTS",
     * nên ta query PRAGMA table_info trước.
     */
    @SuppressWarnings("unused")
    private static void addColumnIfMissing(Connection c, String table,
                                           String column, String type) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return; // đã có
            }
        }
        try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            System.out.println("[Schema] Migrated: ADD " + table + "." + column);
        }
    }

    private static boolean isUsersEmpty(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static String loadResource(String classpath) throws IOException {
        InputStream in = SchemaInitializer.class.getResourceAsStream(classpath);
        if (in == null) throw new IOException("Không tìm thấy resource " + classpath);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    /**
     * Execute SQL script: tách theo dòng cuối có ';', bỏ qua comment.
     * Khi statement fail, in ra SQL gây lỗi để dễ debug.
     */
    private static void executeScript(Connection c, String script, String label) throws SQLException {
        StringBuilder buf = new StringBuilder();
        int count = 0;
        try (Statement stmt = c.createStatement()) {
            for (String rawLine : script.split("\n")) {
                String trimmed = rawLine.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                buf.append(rawLine).append('\n');
                if (trimmed.endsWith(";")) {
                    String sqlStmt = buf.toString().trim();
                    sqlStmt = sqlStmt.substring(0, sqlStmt.length() - 1).trim();
                    if (!sqlStmt.isEmpty()) {
                        try {
                            stmt.execute(sqlStmt);
                            count++;
                        } catch (SQLException ex) {
                            System.err.println("[Schema:" + label + "] Lỗi statement #" + (count + 1) + ":");
                            System.err.println("---- SQL ----\n" + sqlStmt);
                            System.err.println("---- Error ----\n" + ex.getMessage());
                            throw ex;
                        }
                    }
                    buf.setLength(0);
                }
            }
        }
        System.out.println("[Schema] " + label + ": executed " + count + " statement");
    }
}
