package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for sending a message.
 */
public class SendMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageHandler.class);
    private final MessageService messageService = new MessageService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("senderId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or senderId");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            long senderId = request.get("senderId").getAsLong();
            String content = request.has("content") ? request.get("content").getAsString() : "";

            if (content.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Message content is required");
                return response;
            }

            long msgId = messageService.sendMessage(conversationId, senderId, content);
            response.addProperty("status", "success");
            response.addProperty("messageId", msgId);
            response.addProperty("conversationId", conversationId);
            response.addProperty("senderId", senderId);
            response.addProperty("content", content);

            // Broadcast new message to all members in the conversation
            java.util.List<Long> memberIds = getMemberIds(conversationId);
            
            JsonObject broadcastMsg = new JsonObject();
            broadcastMsg.addProperty("action", "NEW_MESSAGE");
            broadcastMsg.addProperty("conversationId", conversationId);
            broadcastMsg.addProperty("senderId", senderId);
            broadcastMsg.addProperty("content", content);
            broadcastMsg.addProperty("messageId", msgId);

            for (Long memberId : memberIds) {
                com.server.tcp.TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
            }
        } catch (Exception e) {
            logger.error("Send message error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    protected java.util.List<Long> getMemberIds(long conversationId) {
        return new com.server.repository.ConversationRepository().getMemberIds(conversationId);
    }
}
