package com.server.handler.friendship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles GET_FRIENDS action — returns the list of accepted friends.
 */
public class GetFriendsHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetFriendsHandler.class);
    private final FriendshipService friendshipService;

    public GetFriendsHandler(FriendshipService friendshipService) {
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

            JsonArray friends = friendshipService.getFriendList(userId);
            logger.info("[GET_FRIENDS] userId={} | count={}", userId, friends.size());
            response.addProperty("status", "success");
            response.add("friends", friends);
        } catch (Exception e) {
            logger.error("[GET_FRIENDS ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
