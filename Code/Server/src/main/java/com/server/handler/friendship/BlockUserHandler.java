package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles BLOCK_USER action — blocks a user from the current authenticated
 * user.
 */
public class BlockUserHandler {
    private static final Logger logger = LoggerFactory.getLogger(BlockUserHandler.class);
    private final FriendshipService friendshipService;

    public BlockUserHandler(FriendshipService friendshipService) {
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
            boolean ok = friendshipService.blockUser(userId, targetUserId);
            logger.info("[BLOCK_USER] userId={} targetUserId={} ok={}", userId, targetUserId, ok);

            response.addProperty("status", ok ? "success" : "error");
            response.addProperty("message", ok ? "Đã chặn người dùng" : "Không thể chặn người dùng này");
        } catch (Exception e) {
            logger.error("[BLOCK_USER ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
