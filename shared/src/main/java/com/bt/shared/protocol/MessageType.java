package com.bt.shared.protocol;

/**
 * Mọi loại tin nhắn trao đổi giữa Client và Server.
 *
 * Quy ước đặt tên:
 *  - *_REQUEST  : Client → Server, cần response
 *  - *_RESPONSE : Server → Client, trả lời cho request
 *  - *_EVENT    : Server → Client, push không cần request (realtime)
 *  - ERROR      : Server → Client, dùng khi request fail
 *
 * Mỗi {@link Message} luôn có {@code requestId}; với event push không có
 * request gốc thì dùng id trống hoặc id của subscription.
 */
public enum MessageType {
    // ==== AUTH ====
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    REGISTER_REQUEST,
    REGISTER_RESPONSE,

    // ==== AUCTION QUERY ====
    LIST_AUCTIONS_REQUEST,
    LIST_AUCTIONS_RESPONSE,
    GET_AUCTION_REQUEST,
    GET_AUCTION_RESPONSE,
    GET_BID_HISTORY_REQUEST,
    GET_BID_HISTORY_RESPONSE,

    // ==== SELLER CRUD ====
    CREATE_ITEM_REQUEST,
    CREATE_ITEM_RESPONSE,
    LIST_MY_ITEMS_REQUEST,
    LIST_MY_ITEMS_RESPONSE,
    CREATE_AUCTION_REQUEST,
    CREATE_AUCTION_RESPONSE,

    // ==== BIDDING ====
    PLACE_BID_REQUEST,
    PLACE_BID_RESPONSE,

    // ==== AUTO-BID (chức năng nâng cao) ====
    REGISTER_AUTOBID_REQUEST,
    REGISTER_AUTOBID_RESPONSE,
    CANCEL_AUTOBID_REQUEST,
    CANCEL_AUTOBID_RESPONSE,

    // ==== SUBSCRIPTION (Phase 3) ====
    SUBSCRIBE_AUCTION_REQUEST,
    UNSUBSCRIBE_AUCTION_REQUEST,
    SUBSCRIPTION_RESPONSE,

    // ==== SERVER → CLIENT EVENT (Phase 3) ====
    BID_PLACED_EVENT,
    AUCTION_CREATED_EVENT,    // broadcast khi seller tạo phiên mới
    AUCTION_STARTED_EVENT,
    AUCTION_FINISHED_EVENT,
    AUCTION_EXTENDED_EVENT,

    // ==== PAYMENT (Phase 9) ====
    PAY_AUCTION_REQUEST,
    PAY_AUCTION_RESPONSE,
    AUCTION_PAID_EVENT,

    // ==== RATING (Phase 9) ====
    RATE_SELLER_REQUEST,
    RATE_SELLER_RESPONSE,

    // ==== HEARTBEAT (Phase 10) ====
    PING_REQUEST,
    PONG_RESPONSE,

    // ==== ADMIN (Phase 12) ====
    LIST_USERS_REQUEST,
    LIST_USERS_RESPONSE,
    SET_USER_ACTIVE_REQUEST,
    SET_USER_ACTIVE_RESPONSE,

    // ==== ERROR ====
    ERROR_RESPONSE
}
