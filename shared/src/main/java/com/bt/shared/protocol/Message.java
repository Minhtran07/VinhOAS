package com.bt.shared.protocol;

import com.google.gson.JsonElement;

import java.util.UUID;

/**
 * Wrapper bao mọi message trên đường truyền.
 *
 * Format JSON ví dụ (request):
 * <pre>{@code
 * { "type": "LOGIN_REQUEST",
 *   "requestId": "f4a2-...",
 *   "payload": { "username": "alice", "password": "..." } }
 * }</pre>
 *
 * Format JSON ví dụ (event):
 * <pre>{@code
 * { "type": "BID_PLACED_EVENT",
 *   "requestId": "",
 *   "payload": { "auctionId": 1, "amount": 1500.0, ... } }
 * }</pre>
 *
 * Trường {@code payload} là {@link JsonElement} thay vì Object để codec không
 * cần biết kiểu cụ thể của payload — service sẽ tự deserialize sang DTO
 * tương ứng dựa vào {@link MessageType}.
 */
public class Message {

    private MessageType type;
    private String requestId;
    private JsonElement payload;

    public Message() {
        // Cho Gson
    }

    public Message(MessageType type, String requestId, JsonElement payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload;
    }

    /** Sinh request id ngẫu nhiên — client dùng để match response. */
    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public JsonElement getPayload() {
        return payload;
    }

    public void setPayload(JsonElement payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", id=" + requestId + "}";
    }
}
