package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import com.server.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeleteMessageHandler.class);
    private final MessageService messageService = new MessageService();
    private final ConversationRepository conversationRepository = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("messageId") || !request.has("conversationId")) {
                logger.warn("[DELETE_MESSAGE] Remote={} | Missing messageId or conversationId",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields");
                return response;
            }

            long messageId = request.get("messageId").getAsLong();
            long conversationId = request.get("conversationId").getAsLong();

            Long connectedUserId = conn.getUserId();
            if (connectedUserId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            // Verify membership
            java.util.List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            if (!memberIds.contains(connectedUserId)) {
                logger.warn("[DELETE_MESSAGE UNAUTHORIZED] Remote={} | UserId={} | ConversationId={} | Not a member",
                        conn.getRemoteAddress(), connectedUserId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: not a member of this conversation");
                return response;
            }

            try {
                boolean success = messageService.deleteMessage(messageId, connectedUserId.longValue());
                if (success) {
                    response.addProperty("status", "success");
                    response.addProperty("messageId", messageId);
                    response.addProperty("conversationId", conversationId);

                    // Broadcast delete event to members
                    JsonObject event = new JsonObject();
                    event.addProperty("action", "DELETE_MESSAGE_EVENT");
                    event.addProperty("messageId", messageId);
                    event.addProperty("conversationId", conversationId);

                    for (Long memberId : memberIds) {
                        TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
                    }
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Message not found or delete failed");
                }
            } catch (SecurityException e) {
                response.addProperty("status", "error");
                response.addProperty("message", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("[DELETE_MESSAGE ERROR] Remote={} | Error deleting message: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
