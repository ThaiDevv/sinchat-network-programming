package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.MessageStatusService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateStatusHandler {
    private static final Logger logger = LoggerFactory.getLogger(UpdateStatusHandler.class);
    private final MessageStatusService messageStatusService = new MessageStatusService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("userId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or userId");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            long userId = request.get("userId").getAsLong();

            // Security check
            Long connectedUserId = conn.getUserId();
            if (connectedUserId == null || userId != connectedUserId) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: userId mismatch");
                return response;
            }

            messageStatusService.markConversationAsSeen(conversationId, userId);

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("userId", userId);
        } catch (Exception e) {
            logger.error("Error updating message status", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal server error: " + e.getMessage());
        }
        return response;
    }
}
