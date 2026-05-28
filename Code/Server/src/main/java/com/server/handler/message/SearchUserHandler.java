package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.repository.UserRepository;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchUserHandler {
    private static final Logger logger = LoggerFactory.getLogger(SearchUserHandler.class);
    private final UserRepository userRepository;

    public SearchUserHandler() {
        this.userRepository = new UserRepository();
    }

    public SearchUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("query")) {
                logger.warn("[SEARCH_USERS] Remote={} | Missing query parameter",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing query parameter");
                return response;
            }

            String query = request.get("query").getAsString();
            long currentUserId = conn.getUserId() != null ? conn.getUserId() : -1;

            logger.info("[SEARCH_USERS] Remote={} | UserId={} | query='{}' | Searching users",
                    conn.getRemoteAddress(), currentUserId, query);

            JsonArray users = userRepository.searchUsers(query, currentUserId);

            logger.info("[SEARCH_USERS] Remote={} | UserId={} | query='{}' | Found {} users",
                    conn.getRemoteAddress(), currentUserId, query, users.size());

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
