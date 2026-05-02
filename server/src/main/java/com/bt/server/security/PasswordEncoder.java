package com.bt.server.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Wrapper quanh BCrypt cho password hashing.
 *
 * BCrypt tự sinh salt và embed vào hash, nên không cần lưu salt riêng.
 * Cost factor mặc định 10 = ~100ms hash → đủ chậm cho brute-force,
 * đủ nhanh cho UX login.
 *
 * Lưu ý migration: dùng {@link #isLegacyPlaintext} để detect và auto-rehash
 * password chưa được mã hóa (cho seed data cũ).
 */
public final class PasswordEncoder {

    /**
     * BCrypt cost factor.
     *  - 10 = production (~100ms hash)
     *  - 6  = dev/demo (~5ms hash, vẫn đủ chống brute-force trong context bài tập)
     * Nếu cần production thật, đổi lại 10.
     */
    private static final int COST = 6;

    private PasswordEncoder() {}

    /** Hash password với salt random. Trả về chuỗi tự chứa $2a$10$... */
    public static String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password không được null");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /** So sánh raw password với hash đã lưu. Constant-time. */
    public static boolean matches(String rawPassword, String hashed) {
        if (rawPassword == null || hashed == null) return false;
        try {
            return BCrypt.checkpw(rawPassword, hashed);
        } catch (IllegalArgumentException ex) {
            // hashed sai format BCrypt → coi như fail (cũng có thể là plaintext legacy)
            return false;
        }
    }

    /** True nếu chuỗi không phải dạng BCrypt hash → đoán là plaintext. */
    public static boolean isLegacyPlaintext(String stored) {
        if (stored == null) return false;
        return !(stored.startsWith("$2a$") || stored.startsWith("$2b$")
                || stored.startsWith("$2y$"));
    }
}
