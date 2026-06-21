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
    private static final int MAX_MESSAGE_LENGTH = 10000;
    private final MessageService messageService = new MessageService();
    private final com.server.repository.ConversationRepository conversationRepository = new com.server.repository.ConversationRepository();
    private final com.server.repository.MessageStatusRepository messageStatusRepository = new com.server.repository.MessageStatusRepository();
    private final com.server.repository.UserRepository userRepository = new com.server.repository.UserRepository();

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

            // Validate message length
            if (content.length() > MAX_MESSAGE_LENGTH) {
                logger.warn("[SEND_MESSAGE] Remote={} | UserId={} | ContentLength={} | Message too long (max {})",
                        conn.getRemoteAddress(), senderId, content.length(), MAX_MESSAGE_LENGTH);
                response.addProperty("status", "error");
                response.addProperty("message", "Message too long. Maximum " + MAX_MESSAGE_LENGTH + " characters.");
                return response;
            }

            // Security check: verify senderId matches the authenticated user
            Long connectedUserId = conn.getUserId();
            if (connectedUserId == null || senderId != connectedUserId) {
                logger.warn("[SEND_MESSAGE UNAUTHORIZED] Remote={} | ConnectedUserId={} | ClaimedSenderId={} | senderId mismatch or not joined",
                        conn.getRemoteAddress(), connectedUserId, senderId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: senderId mismatch");
                return response;
            }

            // Security check: verify sender is a member of this conversation
            java.util.List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            if (!memberIds.contains(senderId)) {
                logger.warn("[SEND_MESSAGE UNAUTHORIZED] Remote={} | UserId={} | ConversationId={} | sender is not a member of this conversation",
                        conn.getRemoteAddress(), senderId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: not a member of this conversation");
                return response;
            }

            if (content.trim().isEmpty()) {
                logger.warn("[SEND_MESSAGE] Remote={} | UserId={} | ConversationId={} | Empty message content rejected",
                        conn.getRemoteAddress(), senderId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Message content is required");
                return response;
            }

            Long replyToId = null;
            com.server.model.Message repliedMessage = null;
            if (request.has("replyToId") && !request.get("replyToId").isJsonNull()) {
                replyToId = request.get("replyToId").getAsLong();
                repliedMessage = messageService.getMessageById(replyToId);
                if (repliedMessage == null || repliedMessage.getConversationId() != conversationId) {
                    logger.warn("[SEND_MESSAGE] Remote={} | UserId={} | ConversationId={} | Invalid replyToId={}",
                            conn.getRemoteAddress(), senderId, conversationId, replyToId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Reply target is not in this conversation");
                    return response;
                }
            }

            logger.info("[SEND_MESSAGE ATTEMPT] Remote={} | UserId={} | ConversationId={} | ContentLength={} | ReplyToId={}",
                    conn.getRemoteAddress(), senderId, conversationId, content.length(), replyToId);

            long msgId = messageService.sendMessage(conversationId, senderId, content, replyToId);
            logger.info("[SEND_MESSAGE SUCCESS] Remote={} | UserId={} | ConversationId={} | MessageId={} | Message stored",
                    conn.getRemoteAddress(), senderId, conversationId, msgId);
            com.server.model.MessageStatus.Status collectiveStatus = messageStatusRepository.getCollectiveStatus(msgId);

            Long resolvedReplyToId = null;
            String replyToUsername = null;
            String replyToContent = null;
            if (repliedMessage != null) {
                resolvedReplyToId = repliedMessage.getId();
                replyToUsername = repliedMessage.getSenderUsername();
                replyToContent = repliedMessage.getContent();
            }

            response.addProperty("status", "success");
            response.addProperty("messageId", msgId);
            response.addProperty("conversationId", conversationId);
            response.addProperty("senderId", senderId);
            response.addProperty("content", content);
            response.addProperty("messageStatus", collectiveStatus.name());
            if (resolvedReplyToId != null) {
                response.addProperty("replyToId", resolvedReplyToId);
                response.addProperty("replyToUsername", replyToUsername);
                response.addProperty("replyToContent", replyToContent);
            }

            // Broadcast new message to all members in the conversation (reuse memberIds from above)

            logger.info("[SEND_MESSAGE BROADCAST] Remote={} | UserId={} | ConversationId={} | MessageId={} | Broadcasting to {} members",
                    conn.getRemoteAddress(), senderId, conversationId, msgId, memberIds.size());

            String senderUsername = "Unknown";
            com.server.model.User sender = userRepository.findById(senderId);
            if (sender != null) {
                senderUsername = sender.getUsername();
            }

            JsonObject broadcastMsg = new JsonObject();
            broadcastMsg.addProperty("action", "NEW_MESSAGE");
            broadcastMsg.addProperty("conversationId", conversationId);
            broadcastMsg.addProperty("senderId", senderId);
            broadcastMsg.addProperty("senderUsername", senderUsername);
            broadcastMsg.addProperty("content", content);
            broadcastMsg.addProperty("messageId", msgId);
            broadcastMsg.addProperty("messageStatus", collectiveStatus.name());
            if (resolvedReplyToId != null) {
                broadcastMsg.addProperty("replyToId", resolvedReplyToId);
                broadcastMsg.addProperty("replyToUsername", replyToUsername);
                broadcastMsg.addProperty("replyToContent", replyToContent);
            }


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
        return conversationRepository.getMemberIds(conversationId);
    }
}
