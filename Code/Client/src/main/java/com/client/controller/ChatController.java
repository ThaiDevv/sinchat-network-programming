package com.client.controller;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.client.model.ApiResponse;
import com.client.service.ChatService;
import com.client.util.ImageUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.application.Platform;

/**
 * Controller for all chat operations.
 * Extracted from ChatView — handles sending messages, loading conversations,
 * message search, avatar/profile changes, and typing indicators.
 */
public class ChatController {

    private final ChatService chatService;
    private final long currentUserId;
    private final Gson gson = new Gson();

    public ChatController(long currentUserId) {
        this.currentUserId = currentUserId;
        this.chatService = ChatService.getInstance();
    }

    // ---- accessors ----

    public long getCurrentUserId() { return currentUserId; }
    public ChatService getChatService() { return chatService; }

    // ---- conversation & contact ----

    public void loadConversations(Consumer<JsonArray> onSuccess, Runnable onError) {
        asyncCall(
                () -> chatService.getConversations(currentUserId),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            JsonArray data = json.getAsJsonArray("conversations");
                            Platform.runLater(() -> onSuccess.accept(data));
                        } catch (Exception e) {
                            System.err.println("Failed to parse conversations JSON");
                            if (onError != null) Platform.runLater(onError);
                        }
                    }
                }
        );
    }

    public void searchUsers(String query, Consumer<JsonArray> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.searchUsers(currentUserId, query),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            JsonArray users = json.getAsJsonArray("users");
                            Platform.runLater(() -> onSuccess.accept(users));
                        } catch (Exception e) {
                            System.err.println("[searchUsers] Failed to parse response: " + e.getMessage());
                            if (onError != null) Platform.runLater(() -> onError.accept("Không đọc được kết quả tìm kiếm."));
                        }
                    } else {
                        String errMsg = response.message() != null && !response.message().isBlank()
                                ? response.message() : "Không thể tìm kiếm người dùng.";
                        System.err.println("[searchUsers] Server error: " + errMsg);
                        if (onError != null) Platform.runLater(() -> onError.accept(errMsg));
                    }
                }
        );
    }

    public void getOrCreateConversation(long peerId, Consumer<Long> onSuccess) {
        asyncCall(
                () -> chatService.getOrCreateConversation(currentUserId, peerId),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            long convId = json.get("conversationId").getAsLong();
                            Platform.runLater(() -> onSuccess.accept(convId));
                        } catch (Exception ignored) {}
                    }
                }
        );
    }

    public void createGroup(String groupName, java.util.List<Long> memberIds,
                            Consumer<Long> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.createGroup(currentUserId, groupName, memberIds),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            long convId = json.get("conversationId").getAsLong();
                            Platform.runLater(() -> onSuccess.accept(convId));
                        } catch (Exception e) {
                            if (onError != null) Platform.runLater(() -> onError.accept("Không đọc được phản hồi từ server."));
                        }
                    } else {
                        String err = response != null && response.message() != null && !response.message().isBlank()
                                ? response.message() : "Không thể tạo nhóm chat.";
                        if (onError != null) Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    public void leaveGroup(long conversationId, Runnable onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.leaveGroup(conversationId, currentUserId),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(onSuccess);
                    } else {
                        String err = response != null && response.message() != null && !response.message().isBlank()
                                ? response.message() : "Không thể rời nhóm.";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    // ---- messages ----

    public void loadMessages(long conversationId, int limit, int offset,
                             Consumer<JsonObject> onSuccess) {
        asyncCall(
                () -> chatService.getMessages(conversationId, limit, offset),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            Platform.runLater(() -> onSuccess.accept(json));
                        } catch (Exception ignored) {}
                    }
                }
        );
    }

    public void sendMessage(long conversationId, String text, Consumer<String> onError) {
        sendMessage(conversationId, text, null, onError);
    }

    public void sendMessage(long conversationId, String text, Long replyToId, Consumer<String> onError) {
        sendMessage(conversationId, text, replyToId, null, onError);
    }

    public void sendMessage(long conversationId, String text, Long replyToId, Long forwardFromId, Consumer<String> onError) {
        asyncCall(
                () -> chatService.sendMessage(conversationId, currentUserId, text, replyToId, forwardFromId),
                response -> {
                    if (!response.isSuccess()) {
                        Platform.runLater(() -> onError.accept(response.message()));
                    }
                }
        );
        // Stop typing after sending
        chatService.sendTyping(conversationId, -1, false);
    }

    /**
     * Forward a message to a target conversation.
     * The forwarded message's original sender and content are carried as forwardFromId.
     * The user may optionally add their own text.
     */
    public void forwardMessage(long targetConversationId, String text, long forwardFromId, Consumer<String> onError) {
        sendMessage(targetConversationId, text, null, forwardFromId, onError);
    }

    public void sendTyping(long conversationId, boolean isTyping) {
        chatService.sendTyping(conversationId, -1, isTyping);
    }

    public void markMessageSeen(long conversationId, long messageId) {
        chatService.updateMessageStatus(conversationId, messageId, "SEEN");
    }

    /** Mark all messages in a conversation as seen for the current user. */
    public void markAllMessagesSeen(long conversationId) {
        chatService.updateMessageStatus(conversationId, "SEEN");
    }

    // ---- message search ----

    public void searchMessages(long conversationId, String keyword, int limit, int offset,
                               Consumer<JsonObject> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.searchMessages(conversationId, keyword, limit, offset),
                response -> {
                    if (response != null && response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            Platform.runLater(() -> onSuccess.accept(json));
                        } catch (Exception e) {
                            Platform.runLater(() -> onError.accept("Không đọc được kết quả tìm kiếm."));
                        }
                    } else {
                        String err = response != null && response.message() != null && !response.message().isBlank()
                                ? response.message() : "Không tìm được tin nhắn.";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    // ---- profile ----

    public void changeUsername(String newName, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.changeUsername(currentUserId, newName),
                response -> {
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(newName));
                    } else {
                        String errMsg = response.message() != null && !response.message().isBlank()
                                ? response.message() : "Không thể đổi tên. Vui lòng thử lại.";
                        Platform.runLater(() -> onError.accept(errMsg));
                    }
                }
        );
    }

    public void changePassword(String oldPassword, String newPassword,
                               Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.changePassword(currentUserId, oldPassword, newPassword),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept("Đổi mật khẩu thành công."));
                    } else {
                        String errMsg = mapChangePasswordError(response);
                        Platform.runLater(() -> onError.accept(errMsg));
                    }
                }
        );
    }

    private String mapChangePasswordError(ApiResponse response) {
        if (response == null || response.message() == null || response.message().isBlank()) {
            return "Không nhận được phản hồi từ server.";
        }
        return switch (response.message()) {
            case "Current password is incorrect" -> "Mật khẩu hiện tại không đúng.";
            case "New password must be at least 6 characters" -> "Mật khẩu mới phải có ít nhất 6 ký tự.";
            case "User not found" -> "Không tìm thấy tài khoản.";
            case "Unauthorized password change request" -> "Phiên đăng nhập không hợp lệ.";
            default -> response.message();
        };
    }

    // ---- avatar ----

    public void loadUserProfile(Consumer<JsonObject> onSuccess) {
        asyncCall(
                () -> chatService.getUserProfile(currentUserId),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject profile = JsonParser.parseString(response.rawBody()).getAsJsonObject();
                            Platform.runLater(() -> onSuccess.accept(profile));
                        } catch (Exception ignored) {}
                    }
                }
        );
    }

    public void loadPeerAvatar(long peerId, Consumer<String> onSuccess, Runnable onDefault) {
        asyncCall(
                () -> chatService.getAvatar(peerId),
                response -> {
                    if (response != null && response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject avatarData = JsonParser.parseString(response.rawBody()).getAsJsonObject();
                            if (avatarData.has("avatarUrl") && !avatarData.get("avatarUrl").isJsonNull()) {
                                String dataUrl = avatarData.get("avatarUrl").getAsString();
                                Platform.runLater(() -> onSuccess.accept(dataUrl));
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (onDefault != null) Platform.runLater(onDefault);
                }
        );
    }

    public void uploadAvatar(javafx.scene.image.Image image, Consumer<String> onSuccess, Consumer<String> onError) {
        String avatarDataUrl = ImageUtils.imageToBase64Png(image);
        if (avatarDataUrl == null) {
            Platform.runLater(() -> onError.accept("Không thể đọc dữ liệu ảnh"));
            return;
        }
        asyncCall(
                () -> chatService.changeAvatar(currentUserId, avatarDataUrl),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept("Cập nhật avatar thành công!"));
                    } else {
                        String err = response != null ? response.message() : "Lỗi kết nối server";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    // ---- friendship ----

    public void getFriendshipStatus(long peerId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.getFriendshipStatus(peerId),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            String fs = json.get("friendshipStatus").getAsString();
                            Platform.runLater(() -> onSuccess.accept(fs));
                        } catch (Exception e) {
                            if (onError != null) Platform.runLater(() -> onError.accept("Parse error"));
                        }
                    } else {
                        if (onError != null) Platform.runLater(() -> onError.accept(response.message()));
                    }
                }
        );
    }

    public void sendFriendRequest(long targetUserId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.sendFriendRequest(targetUserId),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void respondFriendRequest(long requesterId, String decision, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.respondFriendRequest(requesterId, decision),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void cancelFriendRequest(long targetUserId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.cancelFriendRequest(targetUserId),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void unfriend(long friendId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.unfriend(friendId),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void blockUser(long targetUserId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.blockUser(targetUserId),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void unblockUser(long targetUserId, Consumer<String> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.unblockUser(targetUserId),
                response -> {
                    String msg = response.message() != null ? response.message() : "";
                    if (response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(msg));
                    } else {
                        Platform.runLater(() -> onError.accept(msg));
                    }
                }
        );
    }

    public void getFriendRequests(Consumer<JsonObject> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.getFriendRequests(),
                response -> {
                    if (response.isSuccess() && response.rawBody() != null) {
                        try {
                            JsonObject json = gson.fromJson(response.rawBody(), JsonObject.class);
                            Platform.runLater(() -> onSuccess.accept(json));
                        } catch (Exception e) {
                            if (onError != null) Platform.runLater(() -> onError.accept("Parse error"));
                        }
                    } else {
                        if (onError != null) Platform.runLater(() -> onError.accept(
                                response.message() != null ? response.message() : "Lỗi"));
                    }
                }
        );
    }

    // ---- helpers ----

    private void asyncCall(java.util.concurrent.Callable<ApiResponse> callable,
                           Consumer<ApiResponse> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                return new ApiResponse(0, "error", e.getMessage(), "", null, "");
            }
        }).thenAccept(callback);
    }

    public static String getStatusLabelText(String status) {
        if ("SEEN".equalsIgnoreCase(status)) return "Read";
        if ("DELIVERED".equalsIgnoreCase(status)) return "Delivered";
        return "Sent";
    }

    public void editMessage(long messageId, long conversationId, String content, Consumer<String> onError) {
        asyncCall(
                () -> chatService.editMessage(messageId, conversationId, content),
                response -> {
                    if (response != null && !response.isSuccess()) {
                        Platform.runLater(() -> onError.accept(response.message()));
                    }
                }
        );
    }

    public void deleteMessage(long messageId, long conversationId, Consumer<String> onError) {
        asyncCall(
                () -> chatService.deleteMessage(messageId, conversationId),
                response -> {
                    if (response != null && !response.isSuccess()) {
                        Platform.runLater(() -> onError.accept(response.message()));
                    }
                }
        );
    }

    public void pinMessage(long messageId, long conversationId, Runnable onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.pinMessage(messageId, conversationId),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(onSuccess);
                    } else {
                        String err = response != null ? response.message() : "Không thể ghim tin nhắn";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    public void unpinMessage(long messageId, long conversationId, Runnable onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.unpinMessage(messageId, conversationId),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(onSuccess);
                    } else {
                        String err = response != null ? response.message() : "Không thể bỏ ghim tin nhắn";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }

    public void setPinPolicy(long conversationId, boolean adminOnly,
                             Consumer<Boolean> onSuccess, Consumer<String> onError) {
        asyncCall(
                () -> chatService.setPinPolicy(conversationId, adminOnly),
                response -> {
                    if (response != null && response.isSuccess()) {
                        Platform.runLater(() -> onSuccess.accept(adminOnly));
                    } else {
                        String err = response != null ? response.message() : "Không thể cập nhật chính sách ghim";
                        Platform.runLater(() -> onError.accept(err));
                    }
                }
        );
    }
}

