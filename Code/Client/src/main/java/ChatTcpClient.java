import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatTcpClient {
    private static final String HOST = "localhost";
    private static final int PORT = 3000;

    private static ChatTcpClient instance;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Gson gson = new Gson();

    private final ConcurrentHashMap<String, CompletableFuture<ApiResponse>> pendingRequests = new ConcurrentHashMap<>();

    private Consumer<JsonObject> onNewMessage;
    private Consumer<JsonObject> onUserTyping;
    private Runnable onConnected;
    private Consumer<String> onDisconnected;
    private Consumer<String> onError;

    private ChatTcpClient() {}

    public static synchronized ChatTcpClient getInstance() {
        if (instance == null) {
            instance = new ChatTcpClient();
            instance.connectAsync();
        }
        return instance;
    }

    public void connectAsync() {
        new Thread(() -> {
            try {
                // simple retry logic to ensure connection
                while (socket == null || !socket.isConnected() || socket.isClosed()) {
                    try {
                        socket = new Socket(HOST, PORT);
                        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                        if (onConnected != null) Platform.runLater(onConnected);
                        break;
                    } catch (IOException e) {
                        Thread.sleep(1000);
                    }
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    handleServerMessage(line);
                }
            } catch (Exception e) {
                if (onError != null) Platform.runLater(() -> onError.accept(e.getMessage()));
            } finally {
                if (onDisconnected != null) Platform.runLater(() -> onDisconnected.accept("Connection closed"));
                disconnect();
            }
        }).start();
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Fail all pending requests to prevent blocking
        pendingRequests.forEach((requestId, future) -> {
            future.complete(new ApiResponse(500, "error", "Kết nối đã bị ngắt hoặc không thể thiết lập", null, null, ""));
        });
        pendingRequests.clear();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void setOnNewMessage(Consumer<JsonObject> callback) { this.onNewMessage = callback; }
    public void setOnUserTyping(Consumer<JsonObject> callback) { this.onUserTyping = callback; }
    public void setOnConnected(Runnable callback) { this.onConnected = callback; }
    public void setOnDisconnected(Consumer<String> callback) { this.onDisconnected = callback; }
    public void setOnError(Consumer<String> callback) { this.onError = callback; }

    private void handleServerMessage(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            
            if (json.has("requestId")) {
                String requestId = json.get("requestId").getAsString();
                CompletableFuture<ApiResponse> future = pendingRequests.remove(requestId);
                if (future != null) {
                    String status = json.has("status") ? json.get("status").getAsString() : "";
                    String message = json.has("message") ? json.get("message").getAsString() : "";
                    String code = json.has("code") ? json.get("code").getAsString() : "";
                    Long userId = json.has("userId") ? json.get("userId").getAsLong() : null;
                    int statusCode = "success".equals(status) ? 200 : 400;

                    future.complete(new ApiResponse(statusCode, status, message, code, userId, line));
                    return;
                }
            }

            String action = json.has("action") ? json.get("action").getAsString() : "";
            if ("NEW_MESSAGE".equals(action)) {
                if (onNewMessage != null) Platform.runLater(() -> onNewMessage.accept(json));
            } else if ("TYPING_EVENT".equals(action)) {
                if (onUserTyping != null) Platform.runLater(() -> onUserTyping.accept(json));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ApiResponse sendRequestSync(JsonObject request) {
        // Wait until connected
        int retries = 0;
        while (!isConnected() && retries < 50) {
            try { Thread.sleep(100); retries++; } catch (InterruptedException e) {}
        }
        
        if (!isConnected()) {
            return new ApiResponse(500, "error", "Not connected to server", null, null, "");
        }

        String requestId = UUID.randomUUID().toString();
        request.addProperty("requestId", requestId);

        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        writer.println(gson.toJson(request));

        try {
            return future.get(); // block until response
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            return new ApiResponse(500, "error", "Request timeout or interrupted", null, null, "");
        }
    }

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

    public ApiResponse getMessages(long conversationId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");
        req.addProperty("conversationId", conversationId);
        return sendRequestSync(req);
    }

    public void join(long userId) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "JOIN");
        req.addProperty("userId", userId);
        if (isConnected()) writer.println(gson.toJson(req));
    }

    public void sendMessage(long conversationId, long senderId, String content) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", conversationId);
        req.addProperty("senderId", senderId);
        req.addProperty("content", content);
        if (isConnected()) writer.println(gson.toJson(req));
    }

    public void sendTyping(long conversationId, long memberId, boolean isTyping) {
        JsonObject req = new JsonObject();
        req.addProperty("action", "TYPING");
        req.addProperty("conversationId", conversationId);
        req.addProperty("memberId", memberId);
        req.addProperty("isTyping", isTyping);
        if (isConnected()) writer.println(gson.toJson(req));
    }

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
