package com.bt.server.event;

import com.bt.server.net.ClientConnection;
import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry giữ tất cả {@link ClientConnection} đang kết nối tới server.
 *
 * Khác với {@link AuctionEventBus} (subscribe theo auctionId), registry này
 * dùng cho broadcast TOÀN CỤC — vd: khi tạo phiên mới, mọi client đang ở
 * Dashboard cần được thông báo, dù họ chưa subscribe phiên nào.
 *
 * Singleton, thread-safe.
 */
public class ConnectionRegistry {

    private static volatile ConnectionRegistry instance;

    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();

    private ConnectionRegistry() {}

    public static ConnectionRegistry getInstance() {
        if (instance == null) {
            synchronized (ConnectionRegistry.class) {
                if (instance == null) instance = new ConnectionRegistry();
            }
        }
        return instance;
    }

    public void register(ClientConnection conn) {
        connections.add(conn);
    }

    public void unregister(ClientConnection conn) {
        connections.remove(conn);
    }

    public int size() {
        return connections.size();
    }

    /**
     * Broadcast một message tới tất cả connection đang mở. Connection nào
     * bị lỗi IO sẽ không chặn các connection khác, và sẽ tự được dọn khi
     * router của nó cleanup.
     */
    public void broadcast(MessageType type, Object payload) {
        Message msg = MessageCodec.build(type, "", payload);
        for (ClientConnection conn : connections) {
            if (conn.isClosed()) continue;
            try {
                conn.sendMessage(msg);
            } catch (IOException ex) {
                // Connection có thể đã đóng giữa kiểm tra và send — bỏ qua
            }
        }
    }
}
