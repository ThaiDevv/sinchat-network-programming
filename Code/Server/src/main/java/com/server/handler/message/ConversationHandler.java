package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for creating or retrieving a private conversation between two users.
 */
public class ConversationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConversationHandler.class);
    private final ConversationService conversationService = new ConversationService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("user1Id") || !request.has("user2Id")) {
                logger.warn("[GET_OR_CREATE_CONVERSATION] Remote={} | Missing user1Id or user2Id",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing user1Id or user2Id");
                return response;
            }
            long user1Id = request.get("user1Id").getAsLong();
            long user2Id = request.get("user2Id").getAsLong();

            // Security: verify the requesting user is one of the two parties
            Long connUserId = conn.getUserId();
            if (connUserId == null || (connUserId != user1Id && connUserId != user2Id)) {
                logger.warn("[GET_OR_CREATE_CONVERSATION] Remote={} | ConnUserId={} | user1Id={} | user2Id={} | Unauthorized",
                        conn.getRemoteAddress(), connUserId, user1Id, user2Id);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: userId mismatch");
                return response;
            }

            logger.info("[GET_OR_CREATE_CONVERSATION] Remote={} | UserId={} | user1Id={} | user2Id={} | Getting or creating private conversation",
                    conn.getRemoteAddress(), conn.getUserId(), user1Id, user2Id);

            long convId = conversationService.getOrCreatePrivateConversation(user1Id, user2Id);

            logger.info("[GET_OR_CREATE_CONVERSATION] Remote={} | UserId={} | ConversationId={} | Success (users: {}, {})",
                    conn.getRemoteAddress(), conn.getUserId(), convId, user1Id, user2Id);

            response.addProperty("status", "success");
            response.addProperty("conversationId", convId);
        } catch (Exception e) {
            logger.error("[GET_OR_CREATE_CONVERSATION ERROR] Remote={} | user1Id={} | user2Id={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("user1Id") ? request.get("user1Id").getAsLong() : "?",
                    request.has("user2Id") ? request.get("user2Id").getAsLong() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
