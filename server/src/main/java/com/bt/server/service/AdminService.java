package com.bt.server.service;

import com.bt.server.dao.UserDAO;
import com.bt.shared.UserRole;
import com.bt.shared.exception.ValidationException;
import com.bt.shared.protocol.dto.SetUserActiveResponse;
import com.bt.shared.protocol.dto.UserSummaryDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service xử lý nghiệp vụ admin: quản lý user (list, ban/unban).
 *
 * Mọi method ở đây giả định caller đã được xác thực là role ADMIN — việc
 * enforce role được làm ở {@code RequestRouter} trước khi gọi vào.
 *
 * Lý do tách Service: gom logic + invariant (vd: không cho admin tự ban
 * chính mình) ra khỏi DAO và Router — Router chỉ làm marshalling, DAO chỉ
 * làm DB I/O.
 */
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserDAO userDAO;

    public AdminService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Liệt kê user theo filter. Cả 2 filter đều optional (null = bỏ qua).
     */
    public List<UserSummaryDto> listUsers(UserRole roleFilter, Boolean activeFilter) {
        return userDAO.listForAdmin(roleFilter, activeFilter);
    }

    /**
     * Ban hoặc unban user.
     *
     * Invariant:
     *  - Không cho admin tự ban chính mình (tránh khoá luôn quyền admin).
     *  - Target user phải tồn tại.
     *
     * @param adminUserId  id của admin đang thực hiện hành động (lấy từ session)
     * @param targetUserId id của user cần ban/unban
     * @param active       true = mở (unban), false = khoá (ban)
     */
    public SetUserActiveResponse setUserActive(long adminUserId, long targetUserId,
                                               boolean active) throws ValidationException {
        if (adminUserId == targetUserId) {
            throw new ValidationException(
                    "Admin không thể tự thay đổi trạng thái tài khoản của chính mình");
        }
        Optional<Boolean> current = userDAO.isActive(targetUserId);
        if (current.isEmpty()) {
            throw new ValidationException("User không tồn tại: id=" + targetUserId);
        }
        if (current.get() == active) {
            // Không cần update — trả về thành công không-op cho UI khỏi báo fail
            log.info("setUserActive no-op: target={} already active={}", targetUserId, active);
            return new SetUserActiveResponse(targetUserId, active, true);
        }
        boolean ok = userDAO.setActive(targetUserId, active);
        log.info("Admin {} {} user {}: success={}",
                adminUserId, active ? "UNBAN" : "BAN", targetUserId, ok);
        return new SetUserActiveResponse(targetUserId, active, ok);
    }
}
