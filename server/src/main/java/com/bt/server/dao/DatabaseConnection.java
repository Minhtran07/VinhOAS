package com.bt.server.dao;

import com.bt.server.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối DB qua HikariCP connection pool.
 *
 * Mặc định dùng SQLite. Schema được khởi tạo lần đầu (xem
 * {@link SchemaInitializer}) — gọi tách rời với việc tạo pool để dễ debug
 * và để scheduler không bị block khi schema đang load.
 *
 * SQLite chỉ hỗ trợ 1 writer cùng lúc nên pool size nhỏ (4 là đủ).
 */
public final class DatabaseConnection {

    private static volatile HikariDataSource dataSource;
    private static volatile boolean schemaInitialized;

    private DatabaseConnection() {}

    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatabaseConnection.class) {
                if (dataSource == null) {
                    dataSource = buildDataSource();
                }
            }
        }
        // Schema init sau khi pool đã ready, tách khỏi synchronized chính
        // để tránh nested-init nếu SchemaInitializer cần thêm connection.
        if (!schemaInitialized) {
            synchronized (SchemaInitializer.class) {
                if (!schemaInitialized) {
                    SchemaInitializer.ensureSchema(dataSource);
                    schemaInitialized = true;
                }
            }
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        Connection c = getDataSource().getConnection();
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException ignored) {
            // Không phải SQLite — bỏ qua
        }
        return c;
    }

    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
        schemaInitialized = false;
    }

    private static HikariDataSource buildDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.dbUrl());
        String user = AppConfig.dbUsername();
        String pwd = AppConfig.dbPassword();
        if (user != null && !user.isEmpty()) cfg.setUsername(user);
        if (pwd != null && !pwd.isEmpty()) cfg.setPassword(pwd);
        cfg.setMaximumPoolSize(AppConfig.dbPoolMaxSize());
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(30 * 60_000);
        cfg.setPoolName("AuctionPool");

        // SQLite: ép tất cả transaction sang IMMEDIATE (RESERVED lock ngay
        // từ BEGIN). Bắt buộc để tránh race "double-bid" khi nhiều bidder
        // hoặc auto-bid engine cùng thao tác trên 1 phiên: với DEFERRED
        // mặc định, 2 txn cùng SELECT current_price cũ → cả 2 INSERT, commit
        // sau ghi đè giá thấp hơn.
        // Property này được sqlite-jdbc ≥ 3.39 hiểu; driver khác sẽ bỏ qua.
        if (AppConfig.dbUrl() != null
                && AppConfig.dbUrl().toLowerCase().startsWith("jdbc:sqlite")) {
            cfg.addDataSourceProperty("transaction_mode", "IMMEDIATE");
        }
        return new HikariDataSource(cfg);
    }

    public static void main(String[] args) {
        try (Connection c = getConnection()) {
            System.out.println("[OK] Kết nối DB: " + c.getMetaData().getURL());
            System.out.println("     Driver: " + c.getMetaData().getDriverName());
        } catch (SQLException e) {
            System.err.println("[FAIL] " + e.getMessage());
        } finally {
            shutdown();
        }
    }
}
