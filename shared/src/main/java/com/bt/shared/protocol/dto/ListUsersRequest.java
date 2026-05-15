package com.bt.shared.protocol.dto;

import com.bt.shared.UserRole;

/**
 * Yêu cầu lấy danh sách user. Admin only.
 *
 * Filter optional:
 *  - {@code roleFilter}: null = mọi role
 *  - {@code activeFilter}: null = mọi trạng thái; true/false = lọc active
 */
public class ListUsersRequest {

    private UserRole roleFilter;
    private Boolean activeFilter;

    public ListUsersRequest() { /* Gson */ }

    public ListUsersRequest(UserRole roleFilter, Boolean activeFilter) {
        this.roleFilter = roleFilter;
        this.activeFilter = activeFilter;
    }

    public UserRole getRoleFilter() { return roleFilter; }
    public void setRoleFilter(UserRole roleFilter) { this.roleFilter = roleFilter; }

    public Boolean getActiveFilter() { return activeFilter; }
    public void setActiveFilter(Boolean activeFilter) { this.activeFilter = activeFilter; }
}
