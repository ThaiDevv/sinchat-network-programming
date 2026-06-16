package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.model.Message;
import com.server.service.MessageService;
import com.server.repository.ConversationRepository;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EditMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(EditMessageHandler.class);
    private final MessageService messageService = new MessageService();
    private final ConversationRepository convRepo = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            if (!request.has("messageId") || !request.has("conversationId") || !request.has("content")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields");
                return response;
            }

            long messageId = request.get("messageId").getAsLong();
            long conversationId = request.get("conversationId").getAsLong();
            String newContent = request.get("content").getAsString();

            if (newContent.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Message content cannot be empty");
                return response;
            }

            // Verify message exists and was sent by this user
            Message msg = messageService.getMessageById(messageId);
            if (msg == null || msg.getConversationId() != conversationId) {
                response.addProperty("status", "error");
                response.addProperty("message", "Message not found");
                return response;
            }

            if (msg.getSenderId() != userId) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: You did not send this message");
                return response;
            }

            boolean success = messageService.editMessage(messageId, newContent);
            if (success) {
                response.addProperty("status", "success");

                // Broadcast edit event to all conversation members
                JsonObject event = new JsonObject();
                event.addProperty("action", "MESSAGE_EDITED_EVENT");
                event.addProperty("conversationId", conversationId);
                event.addProperty("messageId", messageId);
                event.addProperty("content", newContent);
                event.addProperty("isEdited", true);

                List<Long> members = convRepo.getMemberIds(conversationId);
                for (Long memberId : members) {
                    TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
                }
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Failed to update message");
            }
        } catch (Exception e) {
            logger.error("[EDIT_MESSAGE ERROR] Error: {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
