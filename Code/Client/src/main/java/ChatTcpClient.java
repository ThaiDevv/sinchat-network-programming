import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ChatTcpClient {
    private static final String HOST = System.getProperty("tcp.host", System.getenv("TCP_HOST") != null ? System.getenv("TCP_HOST") : "localhost");
    private static final int PORT = Integer.parseInt(System.getProperty("tcp.port", System.getenv("TCP_PORT") != null ? System.getenv("TCP_PORT") : "3000"));

    // TLS: set tls.enabled=true or env TLS_ENABLED=true to use SSLSocket.
    // Set javax.net.ssl.trustStore / javax.net.ssl.trustStorePassword for custom CA.
    private static final boolean TLS_ENABLED = "true".equalsIgnoreCase(
            System.getProperty("tls.enabled", System.getenv("TLS_ENABLED") != null ? System.getenv("TLS_ENABLED") : "false"));

    // Heartbeat: send PING every HEARTBEAT_INTERVAL_MS, timeout after 3 missed pongs.
    private static final long HEARTBEAT_INTERVAL_MS = Long.parseLong(
            System.getProperty("tcp.heartbeat.interval", "15000"));

    private static ChatTcpClient instance;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Gson gson = new Gson();

    private final ConcurrentHashMap<String, CompletableFuture<ApiResponse>> pendingRequests = new ConcurrentHashMap<>();

    private Consumer<JsonObject> onNewMessage;
    private Consumer<JsonObject> onUserTyping;
    private Consumer<JsonObject> onUserStatusChange;
    private Runnable onConnected;
    private Consumer<String> onDisconnected;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile long userId = -1; // last joined userId for re-join after reconnect

    private ScheduledExecutorService heartbeatScheduler;
    private volatile boolean pongReceived = true;

    private ChatTcpClient() {}

    public static synchronized ChatTcpClient getInstance() {
        if (instance == null) {
            instance = new ChatTcpClient();
            instance.connectAsync();
        }
        return instance;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private SocketFactory createSocketFactory() {
        if (TLS_ENABLED) {
            // Configure trust store via system properties if needed:
            // -Djavax.net.ssl.trustStore=path -Djavax.net.ssl.trustStorePassword=pass
            return SSLSocketFactory.getDefault();
        }
        return SocketFactory.getDefault();
    }

    public void connectAsync() {
        new Thread(this::connectLoop, "tcp-connect-loop").start();
    }

    private void connectLoop() {
        while (true) {
            Socket tempSocket = null;
            try {
                tempSocket = createSocketFactory().createSocket(HOST, PORT);
                synchronized (this) {
                    this.reader = new BufferedReader(new InputStreamReader(tempSocket.getInputStream(), "UTF-8"));
                    this.writer = new PrintWriter(new OutputStreamWriter(tempSocket.getOutputStream(), "UTF-8"), true);
                    this.socket = tempSocket;
                }
                reconnecting.set(false);
                pongReceived = true;
                startHeartbeat();

                if (onConnected != null) Platform.runLater(onConnected);
                if (userId > 0) join(userId); // re-join after reconnect

                // ── Read loop ────────────────────────────────────
                String line;
                while ((line = reader.readLine()) != null) {
                    handleServerMessage(line);
                }
            } catch (IOException e) {
                // connection lost or failed
            }

            // cleanup
            stopHeartbeat();
            try { if (tempSocket != null && !tempSocket.isClosed()) tempSocket.close(); } catch (IOException ignored) {}
            failPendingRequests();

            if (onDisconnected != null) Platform.runLater(() -> onDisconnected.accept("Connection closed – reconnecting…"));

            // back-off then retry
            try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
        }
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tcp-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(this::sendPing, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
    }

    private void sendPing() {
        if (!isConnected()) return;
        if (!pongReceived) {
            // server missed previous pong → close socket to trigger reconnect
            disconnect();
            return;
        }
        pongReceived = false;
        JsonObject ping = new JsonObject();
        ping.addProperty("action", "PING");
        ping.addProperty("requestId", "ping-" + UUID.randomUUID());
        writeLine(gson.toJson(ping));
    }

    // ── Message handling ──────────────────────────────────────────────────────

    private void handleServerMessage(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();

            // request/response correlation
            if (json.has("requestId")) {
                String requestId = json.get("requestId").getAsString();
                // ignore internal ping IDs without a registered future
                CompletableFuture<ApiResponse> future = pendingRequests.remove(requestId);
                if (future != null) {
                    String status = json.has("status") ? json.get("status").getAsString() : "";
                    String message = json.has("message") ? json.get("message").getAsString() : "";
                    String code = json.has("code") ? json.get("code").getAsString() : "";
                    Long uid = json.has("userId") ? json.get("userId").getAsLong() : null;
                    int statusCode = "success".equals(status) ? 200 : 400;
                    future.complete(new ApiResponse(statusCode, status, message, code, uid, line));
                    return;
                }
            }

            String action = json.has("action") ? json.get("action").getAsString() : "";
            switch (action) {
                case "NEW_MESSAGE":
                    if (onNewMessage != null) Platform.runLater(() -> onNewMessage.accept(json));
                    break;
                case "TYPING_EVENT":
                    if (onUserTyping != null) Platform.runLater(() -> onUserTyping.accept(json));
                    break;
                case "USER_STATUS_EVENT":
                    if (onUserStatusChange != null) Platform.runLater(() -> onUserStatusChange.accept(json));
                    break;
                case "PING_RESPONSE":
                    pongReceived = true;
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── I/O helpers ───────────────────────────────────────────────────────────

    private synchronized void writeLine(String content) {
        if (writer != null) {
            writer.println(content);
        }
    }

    private void failPendingRequests() {
        pendingRequests.forEach((id, f) ->
                f.complete(new ApiResponse(500, "error", "Kết nối đã bị ngắt hoặc không thể thiết lập", null, null, "")));
        pendingRequests.clear();
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        failPendingRequests();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void setOnNewMessage(Consumer<JsonObject> callback) { this.onNewMessage = callback; }
    public void setOnUserTyping(Consumer<JsonObject> callback) { this.onUserTyping = callback; }
    public void setOnUserStatusChange(Consumer<JsonObject> callback) { this.onUserStatusChange = callback; }
    public void setOnConnected(Runnable callback) { this.onConnected = callback; }
    public void setOnDisconnected(Consumer<String> callback) { this.onDisconnected = callback; }

    // ── Request/response ──────────────────────────────────────────────────────

    private ApiResponse sendRequestSync(JsonObject request) {
        int retries = 0;
        while (!isConnected() && retries < 50) {
            try { Thread.sleep(100); retries++; } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!isConnected()) {
            return new ApiResponse(500, "error", "Not connected to server", null, null, "");
        }

        String requestId = UUID.randomUUID().toString();
        request.addProperty("requestId", requestId);

        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        writeLine(gson.toJson(request));

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return new ApiResponse(500, "error", "Request timeout or interrupted", null, null, "");
        }
    }

    // ── API methods ───────────────────────────────────────────────────────────

    public ApiResponse register(String username, String password, String email) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "REGISTER");
        req.addProperty("username", username);
        req.addProperty("password", password);
        req.addProperty("email", email);
        return sendRequestSync(req);
    }

    public ApiResponse login(String username, String password) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "LOGIN");
        req.addProperty("username", username);
        req.addProperty("password", password);
        return sendRequestSync(req);
    }

    public ApiResponse requestPasswordResetCode(String username) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("username", username);
        return sendRequestSync(req);
    }

    public ApiResponse resetPassword(String code, String password) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("code", code);
        req.addProperty("password", password);
        return sendRequestSync(req);
    }

    public ApiResponse getConversations(long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_CONVERSATIONS");
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    public ApiResponse searchUsers(String query) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEARCH_USERS");
        req.addProperty("query", query);
        return sendRequestSync(req);
    }

    public ApiResponse getOrCreateConversation(long user1Id, long user2Id) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_OR_CREATE_CONVERSATION");
        req.addProperty("user1Id", user1Id);
        req.addProperty("user2Id", user2Id);
        return sendRequestSync(req);
    }

    public ApiResponse getMessages(long conversationId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");
        req.addProperty("conversationId", conversationId);
        return sendRequestSync(req);
    }

    public void join(long userId) {
        this.userId = userId;
        JsonObject req = new JsonObject();
        req.addProperty("action", "JOIN");
        req.addProperty("userId", userId);
        if (isConnected()) writeLine(gson.toJson(req));
    }

    public void sendMessage(long conversationId, long senderId, String content) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", conversationId);
        req.addProperty("senderId", senderId);
        req.addProperty("content", content);
        if (isConnected()) writeLine(gson.toJson(req));
    }

    public void sendTyping(long conversationId, long memberId, boolean isTyping) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "TYPING");
        req.addProperty("conversationId", conversationId);
        if (memberId > 0) {
            req.addProperty("memberId", memberId);
        }
        req.addProperty("isTyping", isTyping);
        if (isConnected()) writeLine(gson.toJson(req));
    }

    // ── ApiResponse ───────────────────────────────────────────────────────────

    public record ApiResponse(
            int statusCode,
            String status,
            String message,
            String code,
            Long userId,
            String rawBody
    ) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300 && !"error".equalsIgnoreCase(status);
        }
    }
}
