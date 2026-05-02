package com.bt.server.service;

import com.bt.server.dao.UserDAO;
import com.bt.server.security.PasswordEncoder;
import com.bt.shared.Admin;
import com.bt.shared.Bidder;
import com.bt.shared.Seller;
import com.bt.shared.User;
import com.bt.shared.UserRole;
import com.bt.shared.exception.AuthenticationException;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.dto.LoginResponse;
import com.bt.shared.protocol.dto.RegisterRequest;
import com.bt.shared.protocol.dto.RegisterResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Logic xác thực: login + register.
 *
 * Phase 11: dùng BCrypt cho password — không lưu plaintext nữa.
 * Có migration tự động: nếu DB còn record password plaintext (seed data cũ),
 * lần đăng nhập đầu tiên thành công sẽ tự rehash và update DB.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public LoginResponse login(String username, String password) throws AuthenticationException {
        long t0 = System.currentTimeMillis();
        if (username == null || password == null) {
            throw new AuthenticationException("Thiếu username hoặc password");
        }
        Optional<User> opt = userDAO.findByUsername(username);
        if (opt.isEmpty()) {
            log.warn("Login fail: KHÔNG TÌM THẤY USER trong DB — username='{}'. "
                    + "Có thể do (1) chưa đăng ký, (2) DAO mapRow throw exception "
                    + "(check log phía trên), (3) username case-sensitive.",
                    username);
            throw new AuthenticationException(
                    "Sai username hoặc password (user '" + username + "' không tìm thấy)");
        }
        User user = opt.get();
        String stored = user.getPassword();

        boolean ok;
        boolean wasLegacy = PasswordEncoder.isLegacyPlaintext(stored);
        if (wasLegacy) {
            ok = stored.equals(password);
            if (ok) {
                String newHash = PasswordEncoder.hash(password);
                user.setPasswordRaw(newHash);
                userDAO.updatePasswordHash(user.getId(), newHash);
                log.info("Migrated plaintext password → BCrypt cho user {}", username);
            }
        } else {
            ok = PasswordEncoder.matches(password, stored);
        }

        long elapsed = System.currentTimeMillis() - t0;
        if (!ok) {
            log.warn("Login fail: SAI PASSWORD — username='{}', legacy={}, elapsed={}ms",
                    username, wasLegacy, elapsed);
            throw new AuthenticationException("Sai username hoặc password");
        }
        log.info("Login OK: {} ({}) in {}ms", username, user.getRole(), elapsed);
        return toLoginResponse(user);
    }

    public RegisterResponse register(RegisterRequest req) throws ValidationException {
        if (req == null || req.getRole() == null) {
            throw new ValidationException("Thiếu role");
        }
        if (req.getRole() == UserRole.ADMIN) {
            throw new ValidationException("Không thể tự đăng ký ADMIN");
        }
        User.validateOrThrow(req.getUsername(), req.getEmail(), req.getPassword());

        // Hash trước khi tạo entity — entity giữ "đã hash" tới DAO
        String hashed = PasswordEncoder.hash(req.getPassword());
        User newUser;
        switch (req.getRole()) {
            case BIDDER:
                newUser = new Bidder(req.getUsername(), req.getEmail(), hashed, 0.0);
                break;
            case SELLER:
                newUser = new Seller(req.getUsername(), req.getEmail(), hashed, 0.0);
                break;
            default:
                throw new ValidationException("Role không hợp lệ: " + req.getRole());
        }
        Optional<User> saved = userDAO.register(newUser);
        if (saved.isEmpty()) {
            throw new ValidationException(
                    "Username hoặc email đã tồn tại: " + req.getUsername());
        }
        log.info("Registered user {} role {}", req.getUsername(), req.getRole());
        return new RegisterResponse(saved.get().getId(), saved.get().getUsername());
    }

    private LoginResponse toLoginResponse(User u) {
        LoginResponse r = new LoginResponse();
        r.setUserId(u.getId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setRole(u.getRole());
        if (u instanceof Bidder) r.setAccountBalance(((Bidder) u).getAccountBalance());
        if (u instanceof Seller) r.setSellerRating(((Seller) u).getSellerRating());
        if (u instanceof Admin) r.setAccessLevel(((Admin) u).getAccessLevel());
        return r;
    }
}
