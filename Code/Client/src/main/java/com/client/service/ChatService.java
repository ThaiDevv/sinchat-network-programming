package com.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.client.model.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.application.Platform;

/**
 * Core TCP networking service.
 * Handles connection lifecycle, heartbeat, request/response, and server-push events.
 *
 * Refactored from ChatTcpClient — now only responsible for TCP communication.
 * LAN discovery is delegated to {@link LanDiscoveryService}.
 */
public class ChatService {
    private static final boolean TLS_ENABLED = "true".equalsIgnoreCase(
            System.getProperty("tls.enabled", System.getenv("TLS_ENABLED") != null ? System.getenv("TLS_ENABLED") : "false"));
    private static final long HEARTBEAT_INTERVAL_MS = Long.parseLong(
            System.getProperty("tcp.heartbeat.interval", "15000"));

    private static final boolean USE_LAN_DISCOVERY;
    private static String HOST;
    private static int PORT;

    static {
        String explicitHost = System.getProperty("tcp.host",
                System.getenv("TCP_HOST") != null ? System.getenv("TCP_HOST") : null);
        String explicitPort = System.getProperty("tcp.port",
                System.getenv("TCP_PORT") != null ? System.getenv("TCP_PORT") : null);

        USE_LAN_DISCOVERY = (explicitHost == null || explicitHost.isBlank());
        HOST = USE_LAN_DISCOVERY ? "discovering..." : explicitHost;
        PORT = explicitPort != null ? Integer.parseInt(explicitPort) : 3000;

        System.out.println("[TCP] Config loaded: HOST=" + HOST + " PORT=" + PORT +
                " TLS=" + TLS_ENABLED + " LAN_DISCOVERY=" + USE_LAN_DISCOVERY);
    }

    // ---- singleton ----
    private static ChatService instance;

    public static synchronized ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
            instance.connectAsync();
        }
        return instance;
    }

    public static synchronized ChatService getInstanceOrNull() {
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    // ---- instance fields ----
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Gson gson = new Gson();

    private final ConcurrentHashMap<String, CompletableFuture<ApiResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private volatile long userId = -1;
    private ScheduledExecutorService heartbeatScheduler;
    private volatile boolean pongReceived = true;

    private final LanDiscoveryService lanDiscovery = new LanDiscoveryService();

    // ---- callbacks for server-push events ----
    private Consumer<JsonObject> onNewMessage;
    private Consumer<JsonObject> onMessageEdited;
    private Consumer<JsonObject> onMessageDeleted;
    private Consumer<JsonObject> onMessagePinned;
    private Consumer<JsonObject> onUserTyping;
    private Consumer<JsonObject> onUserStatusChange;
    private Consumer<JsonObject> onUserAvatarChanged;
    private Consumer<JsonObject> onMessageStatusChanged;
    private Consumer<JsonObject> onLeftGroup;
    private Consumer<JsonObject> onFriendRequestReceived;
    private Consumer<JsonObject> onFriendAccepted;
    private Runnable onConnected;
    private Consumer<String> onDisconnected;

    private ChatService() {
        if (USE_LAN_DISCOVERY) {
            lanDiscovery.start();
        }
    }

    // ---- public callback setters ----
    public void setOnNewMessage(Consumer<JsonObject> callback) { this.onNewMessage = callback; }
    public void setOnMessageEdited(Consumer<JsonObject> callback) { this.onMessageEdited = callback; }
    public void setOnMessageDeleted(Consumer<JsonObject> callback) { this.onMessageDeleted = callback; }
    public void setOnMessagePinned(Consumer<JsonObject> callback) { this.onMessagePinned = callback; }
    public void setOnUserTyping(Consumer<JsonObject> callback) { this.onUserTyping = callback; }
    public void setOnUserStatusChange(Consumer<JsonObject> callback) { this.onUserStatusChange = callback; }
    public void setOnUserAvatarChanged(Consumer<JsonObject> callback) { this.onUserAvatarChanged = callback; }
    public void setOnMessageStatusChanged(Consumer<JsonObject> callback) { this.onMessageStatusChanged = callback; }
    public void setOnLeftGroup(Consumer<JsonObject> callback) { this.onLeftGroup = callback; }
    public void setOnFriendRequestReceived(Consumer<JsonObject> callback) { this.onFriendRequestReceived = callback; }
    public void setOnFriendAccepted(Consumer<JsonObject> callback) { this.onFriendAccepted = callback; }
    public void setOnConnected(Runnable callback) { this.onConnected = callback; }
    public void setOnDisconnected(Consumer<String> callback) { this.onDisconnected = callback; }

    // ---- connection management ----
    public void connectAsync() {
        new Thread(this::connectLoop, "tcp-connect-loop").start();
    }

    public void shutdown() {
        System.out.println("[TCP] shutdown() requested.");
        shutdownRequested.set(true);
        lanDiscovery.stop();
        disconnect();
    }

    public void disconnect() {
        System.out.println("[TCP] disconnect() called — closing socket...");
        stopHeartbeat();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[TCP] ✗ Error closing socket: " + e.getMessage());
        }
        failPendingRequests();
        System.out.println("[TCP] disconnect() done.");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ---- heartbeat ----
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tcp-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(this::sendPing,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
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
            disconnect();
            return;
        }
        pongReceived = false;
        JsonObject ping = new JsonObject();
        ping.addProperty("action", "PING");
        ping.addProperty("requestId", "ping-" + UUID.randomUUID());
        writeLine(gson.toJson(ping));
    }

    // ---- connect loop ----
    private void connectLoop() {
        System.out.println("[TCP] connectLoop started on thread: " + Thread.currentThread().getName());
        while (!shutdownRequested.get()) {
            // Wait for LAN discovery if needed
            if (USE_LAN_DISCOVERY && "discovering...".equals(HOST)) {
                System.out.println("[TCP] Waiting for LAN discovery to find a SinChat server...");
                int waited = 0;
                while ("discovering...".equals(HOST) && !shutdownRequested.get() && waited < 600) {
                    // Check if LAN discovery has already found a server
                    if (lanDiscovery.hasDiscovered()) {
                        HOST = lanDiscovery.getDiscoveredHost();
                        PORT = lanDiscovery.getDiscoveredPort();
                        break;
                    }
                    try { Thread.sleep(500); waited++; }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                if ("discovering...".equals(HOST)) {
                    System.err.println("[TCP] ✗ LAN discovery timed out after 5 min. Retrying...");
                    try { Thread.sleep(2000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    continue;
                }
                System.out.println("[TCP] LAN discovery resolved: " + HOST + ":" + PORT);
            }

            Socket tempSocket = null;
            try {
                System.out.println("[TCP] Attempting connection to " + HOST + ":" + PORT + " ...");
                long t0 = System.currentTimeMillis();
                tempSocket = createSocketFactory().createSocket(HOST, PORT);
                long dt = System.currentTimeMillis() - t0;
                System.out.println("[TCP] ✓ Connected to " + HOST + ":" + PORT + " in " + dt + "ms");

                synchronized (this) {
                    this.reader = new BufferedReader(new InputStreamReader(tempSocket.getInputStream(), "UTF-8"));
                    this.writer = new PrintWriter(new OutputStreamWriter(tempSocket.getOutputStream(), "UTF-8"), true);
                    this.socket = tempSocket;
                }
                reconnecting.set(false);
                pongReceived = true;
                startHeartbeat();

                if (onConnected != null) Platform.runLater(onConnected);
                if (userId > 0) {
                    System.out.println("[TCP] Re-joining as userId=" + userId);
                    join(userId);
                }

                // Read loop
                String line;
                while ((line = reader.readLine()) != null) {
                    handleServerMessage(line);
                }
                System.out.println("[TCP] reader.readLine() returned null — server closed connection");
            } catch (IOException e) {
                System.err.println("[TCP] ✗ Connection error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }

            // Cleanup and retry
            stopHeartbeat();
            try { if (tempSocket != null && !tempSocket.isClosed()) tempSocket.close(); } catch (IOException ignored) {}
            failPendingRequests();

            if (onDisconnected != null) Platform.runLater(() -> onDisconnected.accept("Connection closed – reconnecting…"));

            if (!shutdownRequested.get()) {
                try { Thread.sleep(2000); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private SocketFactory createSocketFactory() {
        if (TLS_ENABLED) {
            return SSLSocketFactory.getDefault();
        }
        return SocketFactory.getDefault();
    }

    // ---- server message handling ----
    private void handleServerMessage(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();

            // Match response to pending request via requestId
            if (json.has("requestId")) {
                String requestId = json.get("requestId").getAsString();
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

            // Server-push events
            String action = json.has("action") ? json.get("action").getAsString() : "";
            switch (action) {
                case "NEW_MESSAGE":
                    if (onNewMessage != null) Platform.runLater(() -> onNewMessage.accept(json));
                    break;
                case "EDIT_MESSAGE_EVENT":
                    if (onMessageEdited != null) Platform.runLater(() -> onMessageEdited.accept(json));
                    break;
                case "DELETE_MESSAGE_EVENT":
                    if (onMessageDeleted != null) Platform.runLater(() -> onMessageDeleted.accept(json));
                    break;
                case "PIN_MESSAGE_EVENT":
                case "UNPIN_MESSAGE_EVENT":
                    if (onMessagePinned != null) Platform.runLater(() -> onMessagePinned.accept(json));
                    break;
                case "MESSAGE_STATUS_EVENT":
                    if (onMessageStatusChanged != null) Platform.runLater(() -> onMessageStatusChanged.accept(json));
                    break;
                case "TYPING_EVENT":
                    if (onUserTyping != null) Platform.runLater(() -> onUserTyping.accept(json));
                    break;
                case "USER_STATUS_EVENT":
                    if (onUserStatusChange != null) Platform.runLater(() -> onUserStatusChange.accept(json));
                    break;
                case "USER_AVATAR_CHANGED_EVENT":
                    if (onUserAvatarChanged != null) Platform.runLater(() -> onUserAvatarChanged.accept(json));
                    break;
                case "FRIEND_REQUEST_EVENT":
                    if (onFriendRequestReceived != null) Platform.runLater(() -> onFriendRequestReceived.accept(json));
                    break;
                case "FRIEND_ACCEPTED_EVENT":
                    if (onFriendAccepted != null) Platform.runLater(() -> onFriendAccepted.accept(json));
                    break;
                case "PING_RESPONSE":
                    pongReceived = true;
                    break;
                case "LEFT_GROUP":
                    if (onLeftGroup != null) Platform.runLater(() -> onLeftGroup.accept(json));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- I/O helpers ----
    private synchronized void writeLine(String content) {
        if (writer != null) {
            writer.println(content);
            if (writer.checkError()) {
                System.err.println("[TCP] ✗ PrintWriter.checkError() = true after write!");
            }
        } else {
            System.err.println("[TCP] ✗ writeLine: writer is NULL! Not connected yet?");
        }
    }

    private void failPendingRequests() {
        pendingRequests.forEach((id, f) ->
                f.complete(new ApiResponse(500, "error", "Kết nối đã bị ngắt hoặc không thể thiết lập", null, null, "")));
        pendingRequests.clear();
    }


    // ---- request/response ----
    private ApiResponse sendRequestSync(JsonObject request) {
        String action = request.has("action") ? request.get("action").getAsString() : "?";

        int retries = 0;
        while (!isConnected() && retries < 50) {
            try { Thread.sleep(100); retries++; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!isConnected()) {
            return new ApiResponse(500, "error", "Chưa kết nối được server TCP", null, null, "");
        }

        String requestId = UUID.randomUUID().toString();
        request.addProperty("requestId", requestId);

        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        writeLine(gson.toJson(request));

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return new ApiResponse(500, "error", "Server TCP phản hồi quá lâu hoặc request bị ngắt", null, null, "");
        }
    }

    // ---- API methods ----

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

    public ApiResponse changeUsername(long userId, String newUsername) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "CHANGE_NAME");
        req.addProperty("userId", userId);
        req.addProperty("newUsername", newUsername);
        return sendRequestSync(req);
    }

    public ApiResponse changePassword(long userId, String oldPassword, String newPassword) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "CHANGE_PASSWORD");
        req.addProperty("userId", userId);
        req.addProperty("oldPassword", oldPassword);
        req.addProperty("newPassword", newPassword);
        return sendRequestSync(req);
    }

    public ApiResponse getConversations(long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_CONVERSATIONS");
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    public ApiResponse searchUsers(long userId, String query) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEARCH_USERS");
        req.addProperty("query", query);
        req.addProperty("userId", userId);
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

    public ApiResponse getMessages(long conversationId, int limit, int offset) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");
        req.addProperty("conversationId", conversationId);
        req.addProperty("limit", limit);
        req.addProperty("offset", offset);
        return sendRequestSync(req);
    }

    public ApiResponse searchMessages(long conversationId, String keyword, int limit, int offset) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEARCH_MESSAGES");
        req.addProperty("conversationId", conversationId);
        req.addProperty("keyword", keyword);
        req.addProperty("limit", limit);
        req.addProperty("offset", offset);
        return sendRequestSync(req);
    }

    public ApiResponse join(long userId) {
        this.userId = userId;
        JsonObject req = new JsonObject();
        req.addProperty("action", "JOIN");
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    public ApiResponse sendMessage(long conversationId, long senderId, String content) {
        return sendMessage(conversationId, senderId, content, null, null);
    }

    public ApiResponse sendMessage(long conversationId, long senderId, String content, Long replyToId) {
        return sendMessage(conversationId, senderId, content, replyToId, null);
    }

    public ApiResponse sendMessage(long conversationId, long senderId, String content, Long replyToId, Long forwardFromId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", conversationId);
        req.addProperty("senderId", senderId);
        req.addProperty("content", content);
        if (replyToId != null) {
            req.addProperty("replyToId", replyToId);
        }
        if (forwardFromId != null) {
            req.addProperty("forwardFromId", forwardFromId);
        }
        return sendRequestSync(req);
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

    public ApiResponse changeAvatar(long userId, String avatarUrl) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "CHANGE_AVATAR");
        req.addProperty("userId", userId);
        req.addProperty("avatarUrl", avatarUrl);
        return sendRequestSync(req);
    }

    public ApiResponse getUserProfile(long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_PROFILE");
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    public ApiResponse getAvatar(long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_AVATAR");
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    public ApiResponse createGroup(long creatorId, String groupName, java.util.List<Long> memberIds) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "CREATE_GROUP");
        req.addProperty("creatorId", creatorId);
        req.addProperty("groupName", groupName);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (Long id : memberIds) arr.add(id);
        req.add("memberIds", arr);
        return sendRequestSync(req);
    }

    public ApiResponse leaveGroup(long conversationId, long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "LEAVE_GROUP");
        req.addProperty("conversationId", conversationId);
        req.addProperty("userId", userId);
        return sendRequestSync(req);
    }

    // ---- message status helpers ----
    public void updateMessageStatus(long conversationId, String status) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UPDATE_MESSAGE_STATUS");
        req.addProperty("conversationId", conversationId);
        req.addProperty("status", status);
        if (isConnected()) writeLine(gson.toJson(req));
    }

    public void updateMessageStatus(long conversationId, long messageId, String status) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UPDATE_MESSAGE_STATUS");
        req.addProperty("conversationId", conversationId);
        req.addProperty("messageId", messageId);
        req.addProperty("status", status);
        if (isConnected()) writeLine(gson.toJson(req));
    }

    // ---- friendship API methods ----

    public ApiResponse sendFriendRequest(long targetUserId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_FRIEND_REQUEST");
        req.addProperty("targetUserId", targetUserId);
        return sendRequestSync(req);
    }

    public ApiResponse respondFriendRequest(long requesterId, String decision) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "RESPOND_FRIEND_REQUEST");
        req.addProperty("requesterId", requesterId);
        req.addProperty("decision", decision);
        return sendRequestSync(req);
    }

    public ApiResponse cancelFriendRequest(long targetUserId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UNFRIEND");
        req.addProperty("friendId", targetUserId);
        req.addProperty("subAction", "CANCEL_REQUEST");
        return sendRequestSync(req);
    }

    public ApiResponse getFriendRequests() {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_FRIEND_REQUESTS");
        return sendRequestSync(req);
    }

    public ApiResponse getFriendshipStatus(long peerId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_FRIENDSHIP_STATUS");
        req.addProperty("targetUserId", peerId);
        return sendRequestSync(req);
    }

    public ApiResponse unfriend(long friendId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UNFRIEND");
        req.addProperty("friendId", friendId);
        return sendRequestSync(req);
    }

    public ApiResponse blockUser(long targetUserId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "BLOCK_USER");
        req.addProperty("targetUserId", targetUserId);
        return sendRequestSync(req);
    }

    public ApiResponse unblockUser(long targetUserId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UNBLOCK_USER");
        req.addProperty("targetUserId", targetUserId);
        return sendRequestSync(req);
    }

    public ApiResponse editMessage(long messageId, long conversationId, String content) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "EDIT_MESSAGE");
        req.addProperty("messageId", messageId);
        req.addProperty("conversationId", conversationId);
        req.addProperty("content", content);
        return sendRequestSync(req);
    }

    public ApiResponse deleteMessage(long messageId, long conversationId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "DELETE_MESSAGE");
        req.addProperty("messageId", messageId);
        req.addProperty("conversationId", conversationId);
        return sendRequestSync(req);
    }

    public ApiResponse pinMessage(long messageId, long conversationId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "PIN_MESSAGE");
        req.addProperty("messageId", messageId);
        req.addProperty("conversationId", conversationId);
        return sendRequestSync(req);
    }

    public ApiResponse unpinMessage(long messageId, long conversationId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "UNPIN_MESSAGE");
        req.addProperty("messageId", messageId);
        req.addProperty("conversationId", conversationId);
        return sendRequestSync(req);
    }

    public ApiResponse setPinPolicy(long conversationId, boolean adminOnly) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SET_PIN_POLICY");
        req.addProperty("conversationId", conversationId);
        req.addProperty("adminOnly", adminOnly);
        return sendRequestSync(req);
    }
}
