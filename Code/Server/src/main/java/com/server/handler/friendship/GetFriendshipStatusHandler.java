package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles GET_FRIENDSHIP_STATUS action — returns current relationship state
 * between the authenticated user and a target user.
 */
public class GetFriendshipStatusHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetFriendshipStatusHandler.class);
    private final FriendshipService friendshipService;

    public GetFriendshipStatusHandler(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long userId = conn.getUserId();
            // If userId is null (not yet authenticated), try using it from session or default to -1
            if (userId == null) userId = -1L;
            
            if (!request.has("targetUserId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing targetUserId");
                return response;
            }

            long targetUserId = request.get("targetUserId").getAsLong();
            String friendshipStatus = friendshipService.getFriendshipStatus(userId, targetUserId);
            logger.info("[GET_FRIENDSHIP_STATUS] userId={} targetUserId={} friendshipStatus={}",
                    userId, targetUserId, friendshipStatus);

            response.addProperty("status", "success");
            response.addProperty("friendshipStatus", friendshipStatus);
        } catch (Exception e) {
            logger.error("[GET_FRIENDSHIP_STATUS ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
