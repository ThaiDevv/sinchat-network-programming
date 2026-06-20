package com.server.handler.friendship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles GET_FRIEND_REQUESTS action.
 * Returns both incoming (pending) and outgoing (sent) friend requests.
 */
public class GetFriendRequestsHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetFriendRequestsHandler.class);
    private final FriendshipService friendshipService;

    public GetFriendRequestsHandler(FriendshipService friendshipService) {
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

            JsonArray pending = friendshipService.getPendingRequests(userId);
            JsonArray sent = friendshipService.getSentRequests(userId);

            logger.info("[GET_FRIEND_REQUESTS] userId={} | incoming={} | sent={}", userId, pending.size(), sent.size());
            response.addProperty("status", "success");
            response.add("pending", pending);
            response.add("sent", sent);
        } catch (Exception e) {
            logger.error("[GET_FRIEND_REQUESTS ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
