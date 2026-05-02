package com.bt.shared;

import com.bt.shared.exception.ValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lớp trừu tượng đại diện cho người dùng hệ thống.
 *
 * Phân cấp:
 *   User  ──▶  Bidder  (tham gia đấu giá, có accountBalance)
 *          ─▶  Seller  (đăng sản phẩm, có sellerRating)
 *          ─▶  Admin   (quản trị, có accessLevel)
 *
 * Validation được tập trung tại {@link #validate(String, String, String)} để
 * dùng chung cho constructor và setter — không lặp code.
 *
 * Ghi chú bảo mật: trường password ở đây là "password đã hash" (server hash trước khi
 * lưu DB). Lớp này chỉ giữ giá trị thuần — không tự hash để tránh lẫn lộn
 * "đã hash hay chưa". Đăng nhập sẽ được xử lý bởi {@code AuthService} ở server.
 */
public abstract class User extends Entity {

    private static final long serialVersionUID = 1L;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int USERNAME_MIN_LEN = 3;
    private static final int USERNAME_MAX_LEN = 50;

    private String username;
    private String email;
    private String password;

    /** Constructor rỗng cho serialization / DAO. */
    protected User() {
        super();
    }

    protected User(String username, String email, String password) {
        super();
        validate(username, email, password);
        this.username = username.trim();
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    /** Validate dữ liệu — dùng chung cho constructor và setter. */
    private static void validate(String username, String email, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        String u = username.trim();
        if (u.length() < USERNAME_MIN_LEN || u.length() > USERNAME_MAX_LEN) {
            throw new IllegalArgumentException(
                    "Username phải có độ dài từ " + USERNAME_MIN_LEN + " đến " + USERNAME_MAX_LEN + " ký tự");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ: " + email);
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password không được để trống");
        }
    }

    /**
     * Validate dùng cho tầng nghiệp vụ (server controller) — throw checked
     * exception để bắt buộc xử lý.
     */
    public static void validateOrThrow(String username, String email, String password)
            throws ValidationException {
        try {
            validate(username, email, password);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        this.username = username.trim();
    }

    /**
     * Set không validate — chỉ dùng khi load từ DB hay nguồn dữ liệu đã
     * được tin tưởng. Tránh cho dữ liệu legacy không vượt qua validation
     * khi mapRow trong DAO.
     */
    public void setUsernameRaw(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ: " + email);
        }
        this.email = email.trim().toLowerCase();
    }

    public void setEmailRaw(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPasswordRaw(String password) {
        this.password = password;
    }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password không được để trống");
        }
        this.password = password;
    }

    /**
     * Vai trò của user. Mỗi subclass cố định trả về một {@link UserRole} cụ thể.
     * Dùng enum thay vì String giúp compile-time safe khi switch/so sánh.
     */
    public abstract UserRole getRole();

    @Override
    public void displayInfo() {
        System.out.println("[" + getRole() + "] id=" + getId()
                + " | username=" + username + " | email=" + email);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{id=" + getId()
                + ", username='" + username + '\''
                + ", role=" + getRole()
                + '}';
    }

    /**
     * Username là unique trong hệ thống nên có thể dùng để equals khi entity
     * chưa có id (chưa persist). Sau khi persist thì equals của Entity (theo id)
     * sẽ được dùng — đó là contract chuẩn.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (getId() != null) return super.equals(o);
        User other = (User) o;
        return Objects.equals(username, other.username);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
