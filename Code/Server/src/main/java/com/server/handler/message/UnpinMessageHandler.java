package com.server.handler.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.model.Message;
import com.server.repository.ConversationRepository;
import com.server.repository.MessageRepository;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;

/**
 * TCP handler for unpinning a message in a conversation.
 *
 * Expected request fields:
 *   - messageId      (long)  — the message to unpin
 *   - conversationId (long)  — the conversation containing the message
 *
 * Permission:
 *   - Private chat: any member may unpin.
 *   - Group chat: if admin_only_pin is enabled, only ADMIN/OWNER may unpin.
 */
public class UnpinMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(UnpinMessageHandler.class);
    private final MessageRepository messageRepository = new MessageRepository();
    private final ConversationRepository conversationRepository = new ConversationRepository();
    private final Gson gson = new Gson();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("messageId") || !request.has("conversationId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing messageId or conversationId");
                return response;
            }

            long messageId = request.get("messageId").getAsLong();
            long conversationId = request.get("conversationId").getAsLong();
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            // Verify membership
            java.util.List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            if (!memberIds.contains(userId)) {
                logger.warn("[UNPIN_MESSAGE] Remote={} | UserId={} | ConversationId={} | Not a member",
                        conn.getRemoteAddress(), userId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: not a member of this conversation");
                return response;
            }

            // Permission check: if GROUP and admin_only_pin is enabled, only ADMIN/OWNER may unpin
            String convType = conversationRepository.getConversationType(conversationId);
            if ("GROUP".equals(convType)) {
                boolean adminOnly = conversationRepository.isAdminOnlyPinEnabled(conversationId);
                if (adminOnly) {
                    String role = conversationRepository.getUserRoleInConversation(conversationId, userId);
                    if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
                        logger.warn("[UNPIN_MESSAGE] Remote={} | UserId={} | ConvId={} | Permission denied: admin-only pinning",
                                conn.getRemoteAddress(), userId, conversationId);
                        response.addProperty("status", "error");
                        response.addProperty("message", "Permission denied: admin-only pinning is enabled");
                        return response;
                    }
                }
            }

            // Perform DB update
            boolean ok = messageRepository.unpinMessage(messageId);
            if (!ok) {
                response.addProperty("status", "error");
                response.addProperty("message", "DB update failed");
                return response;
            }

            // Build response with updated message
            Message updated = messageRepository.findById(messageId);
            response.addProperty("status", "success");
            response.addProperty("action", "UNPIN_MESSAGE_RESPONSE");
            response.add("message", gson.toJsonTree(updated));

            // Broadcast UNPIN_MESSAGE_EVENT to all members
            JsonObject event = new JsonObject();
            event.addProperty("action", "UNPIN_MESSAGE_EVENT");
            event.addProperty("messageId", messageId);
            event.addProperty("conversationId", conversationId);
            if (updated != null) {
                event.add("message", gson.toJsonTree(updated));
            }
            for (Long memberId : memberIds) {
                TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
            }

        } catch (Exception e) {
            logger.error("[UNPIN_MESSAGE ERROR] Remote={} | Error: {}", conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
