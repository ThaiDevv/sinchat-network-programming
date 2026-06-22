package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.server.repository.FriendshipRepository;
import com.server.repository.UserRepository;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchUserHandler {
    private static final Logger logger = LoggerFactory.getLogger(SearchUserHandler.class);
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public SearchUserHandler() {
        this.userRepository = new UserRepository();
        this.friendshipRepository = new FriendshipRepository();
    }

    public SearchUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = new FriendshipRepository();
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            // Security check: require login
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            // If userId is provided in request, verify it matches the authenticated connection
            if (request.has("userId")) {
                long requestUserId = request.get("userId").getAsLong();
                if (requestUserId != userId) {
                    logger.warn("[SEARCH_USERS] Remote={} | ConnUserId={} | RequestUserId={} | Unauthorized",
                            conn.getRemoteAddress(), userId, requestUserId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Unauthorized: userId mismatch");
                    return response;
                }
            }

            if (!request.has("query")) {
                logger.warn("[SEARCH_USERS] Remote={} | Missing query parameter",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing query parameter");
                return response;
            }

            String query = request.get("query").getAsString();

            logger.info("[SEARCH_USERS] Remote={} | UserId={} | query='{}' | Searching users",
                    conn.getRemoteAddress(), userId, query);

            JsonArray users = userRepository.searchUsers(query, userId);

            // Them friendshipStatus cho moi ket qua tim kiem
            for (JsonElement el : users) {
                JsonObject user = el.getAsJsonObject();
                long otherId = user.get("userId").getAsLong();
                String status = friendshipRepository.getFriendshipStatus(userId, otherId);
                user.addProperty("friendshipStatus", status);
            }

            logger.info("[SEARCH_USERS] Remote={} | UserId={} | query='{}' | Found {} users",
                    conn.getRemoteAddress(), userId, query, users.size());

            response.addProperty("status", "success");
            response.add("users", users);
        } catch (Exception e) {
            logger.error("[SEARCH_USERS ERROR] Remote={} | query={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("query") ? request.get("query").getAsString() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
