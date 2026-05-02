package com.bt.client.net;

import com.bt.shared.protocol.Message;
import com.bt.shared.protocol.MessageCodec;
import com.bt.shared.protocol.MessageType;
import com.bt.shared.protocol.ProtocolException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Singleton client-side socket wrapper.
 *
 * Kiến trúc:
 *  - 1 listener thread đọc message liên tục từ server
 *  - Mỗi response được match với CompletableFuture đang chờ qua requestId
 *  - Mỗi server-pushed event được forward tới các listener đăng ký
 *
 * Ưu điểm: caller (controller JavaFX) gọi {@code sendRequest(...)} đồng bộ
 * theo dạng future, không phải tự quản lý thread đọc.
 */
public class ServerConnection {

    private static volatile ServerConnection instance;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Object writeLock = new Object();
    private Thread listenerThread;
    private volatile boolean connected;

    /** Lưu host/port để auto-reconnect khi mất kết nối. */
    private volatile String lastHost;
    private volatile int lastPort;

    /** requestId → future đang chờ response. */
    private final ConcurrentHashMap<String, CompletableFuture<Message>> pending =
            new ConcurrentHashMap<>();

    /** Listeners cho event push (BID_PLACED_EVENT, AUCTION_FINISHED_EVENT,...). */
    private final CopyOnWriteArrayList<Consumer<Message>> eventListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Lifecycle listeners: nhận event "connected" / "disconnected" / "reconnected"
     * để UI có thể hiển thị banner trạng thái mạng.
     */
    public enum LifecycleEvent { CONNECTED, DISCONNECTED, RECONNECTED, RECONNECTING }
    private final CopyOnWriteArrayList<Consumer<LifecycleEvent>> lifecycleListeners =
            new CopyOnWriteArrayList<>();

    /** Heartbeat scheduler: ping định kỳ + auto-reconnect khi rớt. */
    private ScheduledExecutorService heartbeat;
    private static final long HEARTBEAT_INTERVAL_SEC = 10;
    private static final long PING_TIMEOUT_MS = 5_000;
    private static final long RECONNECT_BACKOFF_MS = 2_000;
    private static final int RECONNECT_MAX_ATTEMPTS = 30;

    private ServerConnection() { /* private */ }

    public static ServerConnection getInstance() {
        if (instance == null) {
            synchronized (ServerConnection.class) {
                if (instance == null) instance = new ServerConnection();
            }
        }
        return instance;
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (connected) return;
        this.lastHost = host;
        this.lastPort = port;
        socket = new Socket(host, port);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        reader = MessageCodec.reader(socket.getInputStream());
        writer = MessageCodec.writer(socket.getOutputStream());
        connected = true;

        listenerThread = new Thread(this::listenLoop, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        // Khởi heartbeat 1 lần — tự ping và tự reconnect.
        startHeartbeatIfNeeded();
        notifyLifecycle(LifecycleEvent.CONNECTED);
    }

    public synchronized void disconnect() {
        if (!connected) return;
        connected = false;
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        // Hủy mọi future còn pending
        pending.values().forEach(f -> f.completeExceptionally(
                new IOException("Disconnected")));
        pending.clear();
        notifyLifecycle(LifecycleEvent.DISCONNECTED);
    }

    /** Đóng dứt khoát + dừng heartbeat (dùng khi user logout / app exit). */
    public synchronized void shutdown() {
        if (heartbeat != null) {
            heartbeat.shutdownNow();
            heartbeat = null;
        }
        disconnect();
    }

    public void addLifecycleListener(Consumer<LifecycleEvent> l) {
        lifecycleListeners.add(l);
    }

    public void removeLifecycleListener(Consumer<LifecycleEvent> l) {
        lifecycleListeners.remove(l);
    }

    private void notifyLifecycle(LifecycleEvent ev) {
        for (Consumer<LifecycleEvent> l : lifecycleListeners) {
            try { l.accept(ev); } catch (Exception ignore) {}
        }
    }

    /** Khởi heartbeat 1 lần — tự ping định kỳ, nếu fail → auto reconnect. */
    private void startHeartbeatIfNeeded() {
        if (heartbeat != null && !heartbeat.isShutdown()) return;
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "client-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::heartbeatTick,
                HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void heartbeatTick() {
        if (!connected) {
            tryReconnect();
            return;
        }
        try {
            sendRequestSync(MessageType.PING_REQUEST,
                    new long[] { System.currentTimeMillis() }, PING_TIMEOUT_MS);
        } catch (IOException | TimeoutException ex) {
            // Coi như mất kết nối — đóng socket để listener loop kết thúc và
            // đẩy trạng thái disconnected, sau đó thử reconnect ở tick kế.
            System.err.println("[Heartbeat] Ping fail: " + ex.getMessage()
                    + " — đánh dấu mất kết nối");
            disconnect();
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean reconnecting =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private void tryReconnect() {
        if (lastHost == null) return; // chưa connect lần nào
        // Tránh nhiều thread (heartbeat tick chồng) cùng reconnect song song.
        if (!reconnecting.compareAndSet(false, true)) return;
        try {
            notifyLifecycle(LifecycleEvent.RECONNECTING);
            for (int attempt = 1; attempt <= RECONNECT_MAX_ATTEMPTS; attempt++) {
                if (connected) return; // ai đó (vd user reconnect manual) đã làm rồi
                try {
                    Thread.sleep(RECONNECT_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    synchronized (this) {
                        if (connected) return;
                        socket = new Socket(lastHost, lastPort);
                        socket.setKeepAlive(true);
                        socket.setTcpNoDelay(true);
                        reader = MessageCodec.reader(socket.getInputStream());
                        writer = MessageCodec.writer(socket.getOutputStream());
                        connected = true;
                        listenerThread = new Thread(this::listenLoop, "server-listener");
                        listenerThread.setDaemon(true);
                        listenerThread.start();
                    }
                    System.out.println("[Reconnect] Thành công sau attempt " + attempt);
                    replaySessionIfAny();
                    notifyLifecycle(LifecycleEvent.RECONNECTED);
                    return;
                } catch (IOException ex) {
                    System.err.println("[Reconnect] attempt " + attempt + " fail: "
                            + ex.getMessage());
                }
            }
            System.err.println("[Reconnect] Hết " + RECONNECT_MAX_ATTEMPTS + " attempt — bỏ cuộc");
        } finally {
            reconnecting.set(false);
        }
    }

    /** Sau reconnect, đăng nhập lại nếu Session còn token để giữ tính năng. */
    private void replaySessionIfAny() {
        com.bt.client.session.Session s = com.bt.client.session.Session.get();
        if (!s.isAuthenticated() || s.getReplayPassword() == null) return;
        try {
            // Gửi login request thẳng (không qua AuctionClient để tránh circular).
            String requestId = Message.newRequestId();
            Message msg = MessageCodec.build(MessageType.LOGIN_REQUEST, requestId,
                    new com.bt.shared.protocol.dto.LoginRequest(
                            s.getUsername(), s.getReplayPassword()));
            CompletableFuture<Message> future = new CompletableFuture<>();
            pending.put(requestId, future);
            synchronized (writeLock) {
                MessageCodec.writeMessage(writer, msg);
            }
            future.get(5, TimeUnit.SECONDS);
            System.out.println("[Reconnect] Đã re-login: " + s.getUsername());
        } catch (Exception ex) {
            System.err.println("[Reconnect] Re-login fail: " + ex.getMessage());
        } finally {
            // Pending entry sẽ tự cleanup theo cơ chế bình thường.
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Gửi request và trả về future cho response.
     * Future hoàn thành khi nhận được message có cùng requestId.
     */
    public CompletableFuture<Message> sendRequest(MessageType type, Object payload) {
        if (!connected) {
            CompletableFuture<Message> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Chưa kết nối server"));
            return failed;
        }
        String requestId = Message.newRequestId();
        Message msg = MessageCodec.build(type, requestId, payload);
        CompletableFuture<Message> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            synchronized (writeLock) {
                MessageCodec.writeMessage(writer, msg);
            }
        } catch (IOException e) {
            pending.remove(requestId);
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Helper: chờ response đồng bộ với timeout. */
    public Message sendRequestSync(MessageType type, Object payload, long timeoutMs)
            throws IOException, TimeoutException {
        // Build request manually để có thể cleanup pending khi timeout/exception.
        if (!connected) {
            throw new IOException("Chưa kết nối server");
        }
        String requestId = Message.newRequestId();
        Message msg = MessageCodec.build(type, requestId, payload);
        CompletableFuture<Message> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            synchronized (writeLock) {
                MessageCodec.writeMessage(writer, msg);
            }
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException(cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", ex);
        } finally {
            // Luôn dọn pending để tránh memory leak: nếu timeout/exception,
            // late response đến sau sẽ bị drop ở dispatch (key không có).
            pending.remove(requestId);
        }
    }

    /** Đăng ký listener cho server-pushed event. */
    public void addEventListener(Consumer<Message> listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(Consumer<Message> listener) {
        eventListeners.remove(listener);
    }

    // ---------- Internals ----------

    private void listenLoop() {
        try {
            while (connected) {
                Message msg = MessageCodec.readMessage(reader);
                if (msg == null) {
                    System.err.println("[Client] Server closed connection");
                    break;
                }
                dispatch(msg);
            }
        } catch (IOException e) {
            if (connected) System.err.println("[Client] Listener IO: " + e.getMessage());
        } catch (ProtocolException e) {
            System.err.println("[Client] Protocol error: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void dispatch(Message msg) {
        String reqId = msg.getRequestId();
        if (reqId != null && !reqId.isEmpty() && pending.containsKey(reqId)) {
            // Là response
            CompletableFuture<Message> future = pending.remove(reqId);
            if (future != null) future.complete(msg);
            return;
        }
        // Còn lại là event push
        for (Consumer<Message> listener : eventListeners) {
            try {
                listener.accept(msg);
            } catch (Exception ex) {
                System.err.println("[Client] Listener error: " + ex.getMessage());
            }
        }
    }
}
