package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for retrieving a user's conversations.
 */
public class GetConversationsHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetConversationsHandler.class);
    private final ConversationService conversationService = new ConversationService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("userId")) {
                logger.warn("[GET_USER_CONVERSATIONS] Remote={} | Missing userId",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing userId");
                return response;
            }
            long userId = request.get("userId").getAsLong();

            logger.info("[GET_USER_CONVERSATIONS] Remote={} | UserId={} | Fetching conversations",
                    conn.getRemoteAddress(), userId);

            JsonArray convs = conversationService.getConversationsWithDetails(userId);

            logger.info("[GET_USER_CONVERSATIONS] Remote={} | UserId={} | Found {} conversations",
                    conn.getRemoteAddress(), userId, convs.size());

            response.addProperty("status", "success");
            response.add("conversations", convs);
        } catch (Exception e) {
            logger.error("[GET_USER_CONVERSATIONS ERROR] Remote={} | userId={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("userId") ? request.get("userId").getAsLong() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
