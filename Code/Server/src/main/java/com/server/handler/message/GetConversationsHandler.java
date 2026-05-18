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
                response.addProperty("status", "error");
                response.addProperty("message", "Missing userId");
                return response;
            }
            long userId = request.get("userId").getAsLong();
            JsonArray convs = conversationService.getConversationsWithDetails(userId);
            response.addProperty("status", "success");
            response.add("conversations", convs);
        } catch (Exception e) {
            logger.error("Get conversations error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
