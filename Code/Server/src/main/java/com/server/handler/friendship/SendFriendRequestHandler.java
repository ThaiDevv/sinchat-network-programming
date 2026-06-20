package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.repository.UserRepository;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles SEND_FRIEND_REQUEST action.
 * After success, pushes a real-time FRIEND_REQUEST_EVENT to the receiver if online.
 */
public class SendFriendRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(SendFriendRequestHandler.class);
    private final FriendshipService friendshipService;
    private final UserRepository userRepository;

    public SendFriendRequestHandler(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
        this.userRepository = new UserRepository();
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long senderId = conn.getUserId();
            if (senderId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }
            if (!request.has("targetUserId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing targetUserId");
                return response;
            }

            long targetUserId = request.get("targetUserId").getAsLong();
            String result = friendshipService.sendFriendRequest(senderId, targetUserId);

            logger.info("[SEND_FRIEND_REQUEST] senderId={} targetId={} result={}", senderId, targetUserId, result);

            switch (result) {
                case "sent":
                    response.addProperty("status", "success");
                    response.addProperty("message", "Đã gửi lời mời kết bạn");
                    // Push real-time event den receiver neu online
                    pushFriendRequestEvent(senderId, targetUserId);
                    break;
                case "already_friends":
                    response.addProperty("status", "error");
                    response.addProperty("message", "Hai người đã là bạn bè");
                    break;
                case "pending_sent":
                    response.addProperty("status", "error");
                    response.addProperty("message", "Bạn đã gửi lời mời rồi");
                    break;
                case "pending_received":
                    response.addProperty("status", "error");
                    response.addProperty("message", "Người này đã gửi lời mời cho bạn, hãy chấp nhận");
                    break;
                case "blocked":
                    response.addProperty("status", "error");
                    response.addProperty("message", "Không thể gửi lời mời");
                    break;
                case "self":
                    response.addProperty("status", "error");
                    response.addProperty("message", "Không thể tự kết bạn với chính mình");
                    break;
                default:
                    response.addProperty("status", "error");
                    response.addProperty("message", "Có lỗi xảy ra, vui lòng thử lại");
            }
        } catch (Exception e) {
            logger.error("[SEND_FRIEND_REQUEST ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    private void pushFriendRequestEvent(long senderId, long targetUserId) {
        try {
            // Lay username cua sender de hien thi cho receiver
            com.server.model.User sender = userRepository.findById(senderId);
            String senderName = (sender != null) ? sender.getUsername() : "Ai đó";

            JsonObject event = new JsonObject();
            event.addProperty("action", "FRIEND_REQUEST_EVENT");
            event.addProperty("senderId", senderId);
            event.addProperty("senderName", senderName);
            TcpConnectionManager.getInstance().broadcastToUser(targetUserId, event);
            logger.info("[SEND_FRIEND_REQUEST] Pushed FRIEND_REQUEST_EVENT to userId={}", targetUserId);
        } catch (Exception e) {
            logger.warn("[SEND_FRIEND_REQUEST] Failed to push event to targetUserId={}: {}", targetUserId, e.getMessage());
        }
    }
}
