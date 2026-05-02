package com.bt.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình từ {@code application.properties} trên classpath.
 *
 * Thứ tự ưu tiên:
 *   1. Biến môi trường (vd: AUCTION_DB_URL)
 *   2. Properties file
 *   3. Giá trị mặc định trong code
 *
 * Nhờ vậy có thể chạy cùng codebase ở dev / CI / production mà không sửa code.
 */
public final class AppConfig {

    private static final String CONFIG_FILE = "/application.properties";
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                PROPS.load(in);
            } else {
                System.err.println("[WARN] Không tìm thấy " + CONFIG_FILE
                        + " trên classpath, dùng default + env var");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được config", e);
        }
    }

    private AppConfig() { /* utility */ }

    public static String dbUrl() {
        return resolve("db.url", "AUCTION_DB_URL",
                "jdbc:mysql://localhost:3306/auction_db");
    }

    public static String dbUsername() {
        return resolve("db.username", "AUCTION_DB_USER", "root");
    }

    public static String dbPassword() {
        return resolve("db.password", "AUCTION_DB_PASSWORD", "");
    }

    public static int dbPoolMaxSize() {
        return Integer.parseInt(
                resolve("db.pool.maxSize", "AUCTION_DB_POOL_MAX", "10"));
    }

    public static int serverPort() {
        return Integer.parseInt(
                resolve("server.port", "AUCTION_SERVER_PORT", "1234"));
    }

    public static int serverMaxClients() {
        return Integer.parseInt(
                resolve("server.maxClients", "AUCTION_SERVER_MAX_CLIENTS", "100"));
    }

    /**
     * Lấy giá trị: env var (uppercase, _) ưu tiên trước, rồi property,
     * cuối cùng là default.
     */
    private static String resolve(String propKey, String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) return env;
        return PROPS.getProperty(propKey, defaultValue);
    }
}
