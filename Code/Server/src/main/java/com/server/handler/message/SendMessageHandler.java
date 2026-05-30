package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.MessageService;
import com.server.service.MessageStatusService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for sending a message.
 */
public class SendMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageHandler.class);
    private final MessageService messageService = new MessageService();
    private final MessageStatusService messageStatusService = new MessageStatusService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("senderId")) {
                logger.warn("[SEND_MESSAGE] Remote={} | Missing conversationId or senderId",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or senderId");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            long senderId = request.get("senderId").getAsLong();
            String content = request.has("content") ? request.get("content").getAsString() : "";

            // Security check: verify senderId matches the authenticated user
            Long connectedUserId = conn.getUserId();
            if (connectedUserId == null || senderId != connectedUserId) {
                logger.warn("[SEND_MESSAGE UNAUTHORIZED] Remote={} | ConnectedUserId={} | ClaimedSenderId={} | senderId mismatch or not joined",
                        conn.getRemoteAddress(), connectedUserId, senderId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: senderId mismatch");
                return response;
            }

            if (content.trim().isEmpty()) {
                logger.warn("[SEND_MESSAGE] Remote={} | UserId={} | ConversationId={} | Empty message content rejected",
                        conn.getRemoteAddress(), senderId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Message content is required");
                return response;
            }

            logger.info("[SEND_MESSAGE ATTEMPT] Remote={} | UserId={} | ConversationId={} | ContentLength={}",
                    conn.getRemoteAddress(), senderId, conversationId, content.length());

            long msgId = messageService.sendMessage(conversationId, senderId, content);
            messageStatusService.initializeMessageStatus(msgId, senderId, conversationId);
            logger.info("[SEND_MESSAGE SUCCESS] Remote={} | UserId={} | ConversationId={} | MessageId={} | Message stored and status initialized",
                    conn.getRemoteAddress(), senderId, conversationId, msgId);
            response.addProperty("status", "success");
            response.addProperty("messageId", msgId);
            response.addProperty("conversationId", conversationId);
            response.addProperty("senderId", senderId);
            response.addProperty("content", content);

            // Broadcast new message to all members in the conversation
            java.util.List<Long> memberIds = getMemberIds(conversationId);

            logger.info("[SEND_MESSAGE BROADCAST] Remote={} | UserId={} | ConversationId={} | MessageId={} | Broadcasting to {} members",
                    conn.getRemoteAddress(), senderId, conversationId, msgId, memberIds.size());

            JsonObject broadcastMsg = new JsonObject();
            broadcastMsg.addProperty("action", "NEW_MESSAGE");
            broadcastMsg.addProperty("conversationId", conversationId);
            broadcastMsg.addProperty("senderId", senderId);
            broadcastMsg.addProperty("content", content);
            broadcastMsg.addProperty("messageId", msgId);

            for (Long memberId : memberIds) {
                logger.info("[SEND_MESSAGE BROADCAST] MessageId={} | Broadcasting to memberId={}",
                        msgId, memberId);
                com.server.tcp.TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
            }
        } catch (Exception e) {
            logger.error("[SEND_MESSAGE ERROR] Remote={} | Error sending message: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    protected java.util.List<Long> getMemberIds(long conversationId) {
        return new com.server.repository.ConversationRepository().getMemberIds(conversationId);
    }
}
