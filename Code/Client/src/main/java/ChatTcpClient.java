import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ChatTcpClient {
    // Discovery port — must match LanDiscoveryBroadcaster.DISCOVERY_PORT on server
    private static final int LAN_DISCOVERY_PORT = 9999;

    // Bat TLS neu can chay socket ma hoa thay vi socket thuong.
    // Neu server dung chung chi rieng thi cau hinh them trustStore.
    private static final boolean TLS_ENABLED = "true".equalsIgnoreCase(
            System.getProperty("tls.enabled", System.getenv("TLS_ENABLED") != null ? System.getenv("TLS_ENABLED") : "false"));

    // Heartbeat giup phat hien ket noi TCP bi dut ngam.
    private static final long HEARTBEAT_INTERVAL_MS = Long.parseLong(
            System.getProperty("tcp.heartbeat.interval", "15000"));

    // If no explicit host is set, enable LAN auto-discovery
    private static final boolean USE_LAN_DISCOVERY;

    // Effective host/port — resolve host lazily when LAN discovery is on
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

    private static ChatTcpClient instance;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Gson gson = new Gson();

    private final ConcurrentHashMap<String, CompletableFuture<ApiResponse>> pendingRequests = new ConcurrentHashMap<>();

    private Consumer<JsonObject> onNewMessage;
    private Consumer<JsonObject> onUserTyping;
    private Consumer<JsonObject> onUserStatusChange;
    private Consumer<JsonObject> onUserAvatarChanged;
    private Consumer<JsonObject> onMessageStatusChanged;
    private Runnable onConnected;
    private Consumer<String> onDisconnected;


    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile long userId = -1; // Luu userId de JOIN lai sau khi reconnect.

    private ScheduledExecutorService heartbeatScheduler;
    private volatile boolean pongReceived = true;

    // LAN discovery state
    private static volatile boolean discoveryRunning = false;

    private ChatTcpClient() {
        if (USE_LAN_DISCOVERY) {
            startLanDiscovery();
        }
    }

    /**
     * Scans the local subnet by probing every IP on port 9999 (TCP).
     * When a SinChat server responds with "SINCHAT_SERVER:<port>",
     * HOST and PORT are updated so the connectLoop can proceed.
     *
     * The scanner is smart: it first checks the local machine, then
     * common gateway addresses (.1, .254), and then sweeps the rest.
     */
    private static void startLanDiscovery() {
        if (discoveryRunning) return;
        discoveryRunning = true;

        Thread t = new Thread(() -> {
            System.out.println("[LAN] TCP subnet discovery started (probe port " + LAN_DISCOVERY_PORT + ")");

            while (discoveryRunning) {
                String foundHost = null;
                int foundPort = 3000;

                // Try localhost first
                System.out.println("[LAN] Probing localhost:" + LAN_DISCOVERY_PORT + " ...");
                String[] localResults = probeHost("127.0.0.1");
                if (localResults != null) {
                    foundHost = "127.0.0.1";
                    foundPort = Integer.parseInt(localResults[0]);
                }

                // If not local, get subnet prefix from a network interface
                if (foundHost == null) {
                    String subnet = getLocalSubnetPrefix();
                    if (subnet != null) {
                        System.out.println("[LAN] Scanning subnet " + subnet + ".0/24 ...");
                        // Priority scan: .1 (gateway), .254, then sweep 2..253
                        int[] priorityIps = buildPriorityOrder();
                        for (int lastOctet : priorityIps) {
                            if (!discoveryRunning) break;
                            String ip = subnet + "." + lastOctet;
                            String[] result = probeHost(ip);
                            if (result != null) {
                                foundHost = ip;
                                foundPort = Integer.parseInt(result[0]);
                                break;
                            }
                        }
                    }
                }

                if (foundHost != null) {
                    if (!foundHost.equals(HOST) || PORT != foundPort) {
                        System.out.println("[LAN] ✓ Discovered SinChat server at " + foundHost + ":" + foundPort);
                        HOST = foundHost;
                        PORT = foundPort;
                    }
                    // Found — stop scanning
                    discoveryRunning = false;
                    break;
                }

                if (!discoveryRunning) break;

                // No server found yet — wait before rescanning
                System.out.println("[LAN] No server found — will rescan in 5s...");
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            System.out.println("[LAN] Discovery scanner stopped.");
        }, "lan-discovery-scanner");
        t.setDaemon(true);
        t.start();
    }

    /** Try to connect to host:9999. Returns [portString] if SinChat found, null otherwise. */
    private static String[] probeHost(String host) {
        try (Socket probeSocket = new Socket()) {
            probeSocket.connect(new InetSocketAddress(host, LAN_DISCOVERY_PORT), 200);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(probeSocket.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line != null && line.startsWith("SINCHAT_SERVER:")) {
                return new String[] { line.substring("SINCHAT_SERVER:".length()) };
            }
        } catch (IOException e) {
            // Timeout or refused — expected for non-server IPs
        }
        return null;
    }

    /** Returns "192.168.1" or similar subnet prefix, or null if undetectable. */
    private static String getLocalSubnetPrefix() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                java.net.NetworkInterface iface = en.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (java.util.Enumeration<java.net.InetAddress> addrEn = iface.getInetAddresses();
                     addrEn.hasMoreElements(); ) {
                    java.net.InetAddress addr = addrEn.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress(); // e.g. "192.168.1.5"
                        int lastDot = ip.lastIndexOf('.');
                        if (lastDot > 0) {
                            return ip.substring(0, lastDot); // "192.168.1"
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[LAN] ✗ Cannot detect subnet: " + e.getMessage());
        }
        return null;
    }

    /** Priority order: gateway (.1), then .254, then sweep 2..253 skipping own IP. */
    private static int[] buildPriorityOrder() {
        int[] result = new int[254];
        result[0] = 1;    // gateway
        result[1] = 254;  // common server
        int idx = 2;
        for (int i = 2; i <= 253; i++) {
            result[idx++] = i;
        }
        return result;
    }

    private static void stopLanDiscovery() {
        discoveryRunning = false;
    }

    public static synchronized ChatTcpClient getInstance() {
        if (instance == null) {
            instance = new ChatTcpClient();
            instance.connectAsync();
        }
        return instance;
    }

    public static synchronized ChatTcpClient getInstanceOrNull() {
        return instance;
    }

    // Ket noi TCP

    private SocketFactory createSocketFactory() {
        if (TLS_ENABLED) {
            // Cau hinh trustStore qua JVM args neu can:
            // -Djavax.net.ssl.trustStore=path -Djavax.net.ssl.trustStorePassword=pass
            return SSLSocketFactory.getDefault();
        }
        return SocketFactory.getDefault();
    }

    public void connectAsync() {
        new Thread(this::connectLoop, "tcp-connect-loop").start();
    }

    private void connectLoop() {
        System.out.println("[TCP] connectLoop started on thread: " + Thread.currentThread().getName());
        while (!shutdownRequested.get()) {
            // If LAN discovery is on, wait until we actually discover a server
            if (USE_LAN_DISCOVERY && "discovering...".equals(HOST)) {
                System.out.println("[TCP] Waiting for LAN discovery to find a SinChat server...");
                int waited = 0;
                while ("discovering...".equals(HOST) && !shutdownRequested.get() && waited < 600) {
                    try { Thread.sleep(500); waited++; } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                if ("discovering...".equals(HOST)) {
                    System.err.println("[TCP] ✗ LAN discovery timed out after 5 min — still no server found. Retrying...");
                    try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
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
                System.out.println("[TCP] ✓ Connected to " + HOST + ":" + PORT + " in " + dt + "ms  (local=" + tempSocket.getLocalSocketAddress() + " remote=" + tempSocket.getRemoteSocketAddress() + ")");
                synchronized (this) {
                    this.reader = new BufferedReader(new InputStreamReader(tempSocket.getInputStream(), "UTF-8"));
                    this.writer = new PrintWriter(new OutputStreamWriter(tempSocket.getOutputStream(), "UTF-8"), true);
                    this.socket = tempSocket;
                }
                reconnecting.set(false);
                pongReceived = true;
                startHeartbeat();

                System.out.println("[TCP] Reader/Writer ready, invoking onConnected callback...");
                if (onConnected != null) Platform.runLater(onConnected);
                if (userId > 0) {
                    System.out.println("[TCP] Re-joining as userId=" + userId);
                    join(userId);
                }

                // Doc lien tuc tung dong JSON server gui ve qua socket.
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    if (lineCount <= 3) System.out.println("[TCP] ← recv #" + lineCount + ": " + line);
                    handleServerMessage(line);
                }
                System.out.println("[TCP] reader.readLine() returned null — server closed connection (read " + lineCount + " lines total)");
            } catch (IOException e) {
                // Mat ket noi hoac chua ket noi duoc thi vong lap se thu lai.
                System.err.println("[TCP] ✗ Connection error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }

            // Don trang thai cu truoc khi reconnect.
            System.out.println("[TCP] Cleaning up connection, will retry in 2s...");
            stopHeartbeat();
            try { if (tempSocket != null && !tempSocket.isClosed()) tempSocket.close(); } catch (IOException ignored) {}
            failPendingRequests();

            if (onDisconnected != null) Platform.runLater(() -> onDisconnected.accept("Connection closed – reconnecting…"));

            // Cho ngan mot chut truoc khi thu ket noi lai.
            if (!shutdownRequested.get()) {
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    // Heartbeat

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
            // Server khong tra PONG thi dong socket de reconnect.
            disconnect();
            return;
        }
        pongReceived = false;
        JsonObject ping = new JsonObject();
        ping.addProperty("action", "PING");
        ping.addProperty("requestId", "ping-" + UUID.randomUUID());
        writeLine(gson.toJson(ping));
    }

    // Xu ly du lieu server gui ve

    private void handleServerMessage(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();

            // Ghep response voi request ban dau bang requestId.
            if (json.has("requestId")) {
                String requestId = json.get("requestId").getAsString();
                // Bo qua PING noi bo neu khong co request dang cho.
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

    // Ho tro doc ghi socket

    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public void shutdown() {
        System.out.println("[TCP] shutdown() requested.");
        shutdownRequested.set(true);
        stopLanDiscovery();
        disconnect();
    }

    /**
     * Dung client hien tai va reset singleton de lan dang nhap sau
     * tao mot ket noi TCP moi.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    private synchronized void writeLine(String content) {
        if (writer != null) {
            // Log first few outgoing messages
            String preview = content.length() > 150 ? content.substring(0, 150) + "..." : content;
            System.out.println("[TCP] → send: " + preview);
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

    public void disconnect() {
        System.out.println("[TCP] disconnect() called — closing socket...");
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

    // Callback cho UI

    public void setOnNewMessage(Consumer<JsonObject> callback) { this.onNewMessage = callback; }
    public void setOnUserTyping(Consumer<JsonObject> callback) { this.onUserTyping = callback; }
    public void setOnUserStatusChange(Consumer<JsonObject> callback) { this.onUserStatusChange = callback; }
    public void setOnUserAvatarChanged(Consumer<JsonObject> callback) { this.onUserAvatarChanged = callback; }
    public void setOnMessageStatusChanged(Consumer<JsonObject> callback) { this.onMessageStatusChanged = callback; }
    public void setOnConnected(Runnable callback) { this.onConnected = callback; }
    public void setOnDisconnected(Consumer<String> callback) { this.onDisconnected = callback; }

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


    // Gui request TCP va cho response

    private ApiResponse sendRequestSync(JsonObject request) {
        String action = request.has("action") ? request.get("action").getAsString() : "?";
        System.out.println("[TCP] sendRequestSync(" + action + ") — checking isConnected()...");

        int retries = 0;
        while (!isConnected() && retries < 50) {
            if (retries == 0) System.out.println("[TCP]  Waiting for connection (isConnected=" + isConnected() + ")...");
            try { Thread.sleep(100); retries++; } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!isConnected()) {
            System.err.println("[TCP] ✗ sendRequestSync(" + action + ") TIMEOUT — still not connected after " + (retries * 100) + "ms. socket=" + socket + " isConnected=" + (socket != null ? socket.isConnected() : "null") + " isClosed=" + (socket != null ? socket.isClosed() : "null"));
            return new ApiResponse(500, "error", "Chưa kết nối được server TCP", null, null, "");
        }

        System.out.println("[TCP] sendRequestSync(" + action + ") — connected, sending request...");

        String requestId = UUID.randomUUID().toString();
        request.addProperty("requestId", requestId);

        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        writeLine(gson.toJson(request));

        try {
            ApiResponse resp = future.get(10, TimeUnit.SECONDS);
            System.out.println("[TCP] sendRequestSync(" + action + ") ← response: status=" + resp.status + " message=" + resp.message);
            return resp;
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            System.err.println("[TCP] ✗ sendRequestSync(" + action + ") — timeout or error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            return new ApiResponse(500, "error", "Server TCP phản hồi quá lâu hoặc request bị ngắt", null, null, "");
        }
    }

    // Cac action gui qua TCP

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

    // Method to change username
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
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", conversationId);
        req.addProperty("senderId", senderId);
        req.addProperty("content", content);
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

    // Ket qua tra ve cho UI

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
