package com.bt.shared.protocol.dto;

/** Yêu cầu list các item của seller đang đăng nhập. Server lấy sellerId
 * từ session — không nhận từ client để tránh xem item người khác. */
public class ListMyItemsRequest {

    public ListMyItemsRequest() { /* Gson */ }
}
