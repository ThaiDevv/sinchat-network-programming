package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UNFRIEND action — removes an ACCEPTED friendship.
 * Also handles cancelling a pending sent request (CANCEL_FRIEND_REQUEST).
 */
public class UnfriendHandler {
    private static final Logger logger = LoggerFactory.getLogger(UnfriendHandler.class);
    private final FriendshipService friendshipService;

    public UnfriendHandler(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }
            if (!request.has("friendId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing friendId");
                return response;
            }

            long friendId = request.get("friendId").getAsLong();
            // Doc subAction de phan biet CANCEL_REQUEST vs UNFRIEND
            String subAction = request.has("subAction") ? request.get("subAction").getAsString() : "UNFRIEND";
            boolean ok;

            if ("CANCEL_REQUEST".equals(subAction)) {
                ok = friendshipService.cancelFriendRequest(userId, friendId);
                logger.info("[CANCEL_FRIEND_REQUEST] userId={} targetId={} ok={}", userId, friendId, ok);
                response.addProperty("status", ok ? "success" : "error");
                response.addProperty("message", ok ? "Đã hủy lời mời kết bạn" : "Không tìm thấy lời mời để hủy");
            } else {
                ok = friendshipService.unfriend(userId, friendId);
                logger.info("[UNFRIEND] userId={} friendId={} ok={}", userId, friendId, ok);
                response.addProperty("status", ok ? "success" : "error");
                response.addProperty("message", ok ? "Đã hủy kết bạn" : "Không tìm thấy quan hệ bạn bè để hủy");
            }
        } catch (Exception e) {
            logger.error("[UNFRIEND ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
