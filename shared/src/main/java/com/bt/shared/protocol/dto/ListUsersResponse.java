package com.bt.shared.protocol.dto;

import java.util.List;

/**
 * Response: danh sách user kèm tổng số. Admin only.
 */
public class ListUsersResponse {

    private List<UserSummaryDto> users;
    private int total;

    public ListUsersResponse() { /* Gson */ }

    public ListUsersResponse(List<UserSummaryDto> users) {
        this.users = users;
        this.total = users == null ? 0 : users.size();
    }

    public List<UserSummaryDto> getUsers() { return users; }
    public void setUsers(List<UserSummaryDto> users) {
        this.users = users;
        this.total = users == null ? 0 : users.size();
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
