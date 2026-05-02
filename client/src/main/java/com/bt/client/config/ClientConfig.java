package com.bt.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình client từ {@code com/bt/client.properties}.
 *
 * Tương tự AppConfig của server: env var > properties > default.
 */
public final class ClientConfig {

    private static final String FILE = "/com/bt/client.properties";
    private static final Properties P = new Properties();

    static {
        try (InputStream in = ClientConfig.class.getResourceAsStream(FILE)) {
            if (in != null) P.load(in);
        } catch (IOException ex) {
            System.err.println("[Client] Không đọc được " + FILE + ": " + ex.getMessage());
        }
    }

    private ClientConfig() { /* utility */ }

    public static String serverHost() {
        return resolve("server.host", "AUCTION_SERVER_HOST", "localhost");
    }

    public static int serverPort() {
        return Integer.parseInt(resolve("server.port", "AUCTION_SERVER_PORT", "1234"));
    }

    private static String resolve(String key, String envKey, String def) {
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) return env;
        return P.getProperty(key, def);
    }
}
