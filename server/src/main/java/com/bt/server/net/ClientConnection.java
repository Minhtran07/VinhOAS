package com.bt.server.net;

import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Đại diện cho 1 kết nối client phía server.
 *
 * Trách nhiệm:
 *  - Bọc {@link Socket} với reader/writer JSON-line
 *  - Lưu trạng thái session (userId, username, role, các auction đang subscribe)
 *  - Cung cấp {@link #sendMessage} thread-safe để service và scheduler push
 *    event mà không xung đột với response của router
 *  - Có id duy nhất giúp router/AuctionRegistry track
 */
public class ClientConnection implements AutoCloseable {

    private static final AtomicLong COUNTER = new AtomicLong(1);

    private final long connectionId;
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();

    /** Set các auctionId mà connection đang subscribe (Phase 3 sẽ dùng). */
    private final Set<Long> subscribedAuctions = ConcurrentHashMap.newKeySet();

    private volatile Long userId;
    private volatile String username;
    private volatile String role;
    private volatile boolean closed;

    public ClientConnection(Socket socket) throws IOException {
        this.connectionId = COUNTER.getAndIncrement();
        this.socket = socket;
        this.reader = MessageCodec.reader(socket.getInputStream());
        this.writer = MessageCodec.writer(socket.getOutputStream());
    }

    public long getConnectionId() { return connectionId; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isAuthenticated() { return userId != null; }
    public boolean isClosed() { return closed; }
    public Set<Long> getSubscribedAuctions() { return subscribedAuctions; }

    public void bindUser(long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    /** Đọc message kế tiếp. Trả null khi client đóng kết nối. */
    public Message readMessage() throws IOException {
        return MessageCodec.readMessage(reader);
    }

    /**
     * Gửi message tới client. Đồng bộ trên writer để 2 thread
     * (response + event push) không bị trộn dòng.
     */
    public void sendMessage(Message msg) throws IOException {
        synchronized (writeLock) {
            if (closed) return;
            MessageCodec.writeMessage(writer, msg);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { reader.close(); } catch (IOException ignored) {}
        try { writer.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public String toString() {
        return "Conn#" + connectionId
                + (username != null ? "/" + username : "/anon")
                + "@" + socket.getInetAddress();
    }
}
