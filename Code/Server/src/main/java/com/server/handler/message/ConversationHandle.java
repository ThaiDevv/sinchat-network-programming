package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for creating or retrieving a private conversation between two users.
 */
public class ConversationHandle {
    private static final Logger logger = LoggerFactory.getLogger(ConversationHandle.class);
    private final ConversationService conversationService = new ConversationService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("user1Id") || !request.has("user2Id")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing user1Id or user2Id");
                return response;
            }
            long user1Id = request.get("user1Id").getAsLong();
            long user2Id = request.get("user2Id").getAsLong();

            long convId = conversationService.getOrCreatePrivateConversation(user1Id, user2Id);
            response.addProperty("status", "success");
            response.addProperty("conversationId", convId);
        } catch (Exception e) {
            logger.error("Conversation handle error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
