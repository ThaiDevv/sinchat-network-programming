import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.function.Consumer;

/**
 * WebSocket client wrapper cho JavaFX.
 *
 * Lớp này quản lý kết nối WebSocket đến server, tự động gửi "join"
 * khi kết nối thành công, và dispatch các sự kiện nhận được lên JavaFX thread.
 *
 * Cách dùng:
 *   ChatWebSocketClient ws = new ChatWebSocketClient("ws://localhost:8887", userId);
 *   ws.setOnNewMessage(json -> { ... });  // callback khi có tin nhắn mới
 *   ws.setOnUserTyping(json -> { ... });  // callback khi có người đang gõ
 *   ws.connectAsync();                     // kết nối bất đồng bộ
 */
public class ChatWebSocketClient {
    private final Gson gson = new Gson();
    private final String serverUri;
    private final long userId;
    private WebSocketClient client;

    // Callbacks — được gọi trên JavaFX Application Thread
    private Consumer<JsonObject> onNewMessage;
    private Consumer<JsonObject> onUserTyping;
    private Runnable onConnected;
    private Consumer<String> onDisconnected;
    private Consumer<String> onError;

    public ChatWebSocketClient(String serverUri, long userId) {
        this.serverUri = serverUri;
        this.userId = userId;
    }

    /**
     * Kết nối bất đồng bộ đến WebSocket server.
     * Sau khi kết nối thành công, tự động gửi action "join" với userId.
     */
    public void connectAsync() {
        try {
            client = new WebSocketClient(new URI(serverUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    // Gửi join ngay khi kết nối
                    JsonObject joinMsg = new JsonObject();
                    joinMsg.addProperty("action", "join");
                    joinMsg.addProperty("userId", userId);
                    send(gson.toJson(joinMsg));

                    if (onConnected != null) {
                        Platform.runLater(onConnected);
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleServerMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (onDisconnected != null) {
                        Platform.runLater(() -> onDisconnected.accept(reason));
                    }
                }

                @Override
                public void onError(Exception ex) {
                    if (onError != null) {
                        Platform.runLater(() -> onError.accept(ex.getMessage()));
                    }
                }
            };
            client.connect();
        } catch (Exception e) {
            if (onError != null) {
                Platform.runLater(() -> onError.accept(e.getMessage()));
            }
        }
    }

    /**
     * Gửi tin nhắn qua WebSocket (thay vì HTTP).
     */
    public void sendMessage(long conversationId, String content) {
        if (client == null || !client.isOpen()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("action", "send_message");
        msg.addProperty("conversationId", conversationId);
        msg.addProperty("senderId", userId);
        msg.addProperty("content", content);
        client.send(gson.toJson(msg));
    }

    /**
     * Gửi thông báo "đang gõ" cho conversation.
     */
    public void sendTyping(long conversationId) {
        if (client == null || !client.isOpen()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("action", "typing");
        msg.addProperty("conversationId", conversationId);
        msg.addProperty("userId", userId);
        client.send(gson.toJson(msg));
    }

    /**
     * Đóng kết nối WebSocket.
     */
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Kiểm tra trạng thái kết nối.
     */
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    // ─────────────────── Setters cho callbacks ───────────────────

    public void setOnNewMessage(Consumer<JsonObject> callback) {
        this.onNewMessage = callback;
    }

    public void setOnUserTyping(Consumer<JsonObject> callback) {
        this.onUserTyping = callback;
    }

    public void setOnConnected(Runnable callback) {
        this.onConnected = callback;
    }

    public void setOnDisconnected(Consumer<String> callback) {
        this.onDisconnected = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    // ─────────────────── Internal message handler ───────────────────

    /**
     * Parse JSON từ server và dispatch đến callback tương ứng.
     * Tất cả callback được gọi trên JavaFX Application Thread (Platform.runLater).
     */
    private void handleServerMessage(String rawMessage) {
        try {
            JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
            String action = json.has("action") ? json.get("action").getAsString() : "";

            switch (action) {
                case "new_message" -> {
                    if (onNewMessage != null) {
                        Platform.runLater(() -> onNewMessage.accept(json));
                    }
                }
                case "user_typing" -> {
                    if (onUserTyping != null) {
                        Platform.runLater(() -> onUserTyping.accept(json));
                    }
                }
                case "joined" -> {
                    // Xác nhận join thành công, không cần xử lý đặc biệt
                }
                case "error" -> {
                    String errorMsg = json.has("message") ? json.get("message").getAsString() : "Unknown error";
                    if (onError != null) {
                        Platform.runLater(() -> onError.accept(errorMsg));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
