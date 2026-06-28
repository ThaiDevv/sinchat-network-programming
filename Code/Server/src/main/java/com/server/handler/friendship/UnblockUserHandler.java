package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UNBLOCK_USER action — removes a block created by the current user.
 */
public class UnblockUserHandler {
    private static final Logger logger = LoggerFactory.getLogger(UnblockUserHandler.class);
    private final FriendshipService friendshipService;

    public UnblockUserHandler(FriendshipService friendshipService) {
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
            if (!request.has("targetUserId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing targetUserId");
                return response;
            }

            long targetUserId = request.get("targetUserId").getAsLong();
            boolean ok = friendshipService.unblockUser(userId, targetUserId);
            logger.info("[UNBLOCK_USER] userId={} targetUserId={} ok={}", userId, targetUserId, ok);

            response.addProperty("status", ok ? "success" : "error");
            response.addProperty("message", ok ? "Đã gỡ chặn người dùng" : "Không tìm thấy trạng thái chặn để gỡ");
        } catch (Exception e) {
            logger.error("[UNBLOCK_USER ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
