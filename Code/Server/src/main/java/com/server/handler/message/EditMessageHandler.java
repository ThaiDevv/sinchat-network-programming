package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import com.server.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(EditMessageHandler.class);
    private final MessageService messageService = new MessageService();
    private final ConversationRepository conversationRepository = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("messageId") || !request.has("conversationId") || !request.has("content")) {
                logger.warn("[EDIT_MESSAGE] Remote={} | Missing messageId, conversationId, or content",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields");
                return response;
            }

            long messageId = request.get("messageId").getAsLong();
            long conversationId = request.get("conversationId").getAsLong();
            String content = request.get("content").getAsString();

            if (content.trim().isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Message content cannot be empty");
                return response;
            }

            Long connectedUserId = conn.getUserId();
            if (connectedUserId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            // Verify membership
            java.util.List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            if (!memberIds.contains(connectedUserId)) {
                logger.warn("[EDIT_MESSAGE UNAUTHORIZED] Remote={} | UserId={} | ConversationId={} | Not a member",
                        conn.getRemoteAddress(), connectedUserId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: not a member of this conversation");
                return response;
            }

            try {
                long newMsgId = messageService.editMessage(messageId, connectedUserId.longValue(), content);
                if (newMsgId > 0) {
                    response.addProperty("status", "success");
                    response.addProperty("messageId", messageId);
                    response.addProperty("conversationId", conversationId);
                    response.addProperty("content", content);

                    // Broadcast edit event to members — client will update the old bubble in-place
                    JsonObject event = new JsonObject();
                    event.addProperty("action", "EDIT_MESSAGE_EVENT");
                    event.addProperty("messageId", messageId);
                    event.addProperty("conversationId", conversationId);
                    event.addProperty("content", content);

                    for (Long memberId : memberIds) {
                        TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
                    }
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Message not found or update failed");
                }
            } catch (SecurityException | IllegalStateException e) {
                response.addProperty("status", "error");
                response.addProperty("message", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("[EDIT_MESSAGE ERROR] Remote={} | Error editing message: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
