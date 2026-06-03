package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.model.Message;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TCP handler for retrieving messages of a conversation.
 */
public class GetMessagesHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetMessagesHandler.class);
    private final Gson gson = new Gson();
    private final MessageService messageService = new MessageService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            if (!request.has("conversationId")) {
                logger.warn("[GET_MESSAGES] Remote={} | Missing conversationId",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId");
                return response;
            }
            long conversationId = request.get("conversationId").getAsLong();
            int limit = request.has("limit") ? request.get("limit").getAsInt() : 0;
            int offset = request.has("offset") ? request.get("offset").getAsInt() : 0;

            logger.info("[GET_MESSAGES] Remote={} | UserId={} | ConversationId={} | limit={} | offset={} | Fetching messages",
                    conn.getRemoteAddress(), userId, conversationId, limit, offset);

            // Update statuses to SEEN for the recipient fetching them
            com.server.repository.MessageStatusRepository msRepo = new com.server.repository.MessageStatusRepository();
            msRepo.markAllAsSeen(conversationId, userId);

            // Notify other members that this user has SEEN the messages in the conversation
            java.util.List<Long> memberIds = new com.server.repository.ConversationRepository().getMemberIds(conversationId);
            JsonObject statusEvent = new JsonObject();
            statusEvent.addProperty("action", "MESSAGE_STATUS_EVENT");
            statusEvent.addProperty("conversationId", conversationId);
            statusEvent.addProperty("status", "SEEN");
            statusEvent.addProperty("userId", userId);
            for (Long memberId : memberIds) {
                if (!memberId.equals(userId)) {
                    com.server.tcp.TcpConnectionManager.getInstance().broadcastToUser(memberId, statusEvent);
                }
            }

            List<Message> messages;
            if (limit > 0) {
                // Fetch limit+1 to determine if there are more items beyond the requested page
                int fetchLimit = limit + 1;
                messages = messageService.getMessages(conversationId, fetchLimit, offset);
                boolean hasMore = messages.size() > limit;
                if (hasMore) {
                    messages = messages.subList(0, limit);
                }
                response.addProperty("hasMore", hasMore);
                logger.info("[GET_MESSAGES] Remote={} | ConversationId={} | Fetched {} messages (hasMore={})",
                        conn.getRemoteAddress(), conversationId, messages.size(), hasMore);
            } else {
                messages = messageService.getMessages(conversationId);
                response.addProperty("hasMore", false);
                logger.info("[GET_MESSAGES] Remote={} | ConversationId={} | Fetched {} messages (no pagination)",
                        conn.getRemoteAddress(), conversationId, messages.size());
            }

            // Populate status in messages
            java.util.Map<Long, com.server.model.MessageStatus.Status> statuses = msRepo.getStatusesForConversation(conversationId);
            for (Message msg : messages) {
                com.server.model.MessageStatus.Status status = statuses.get(msg.getId());
                if (status != null) {
                    msg.setStatus(status.name());
                } else {
                    msg.setStatus("SENT");
                }
            }

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("count", messages.size());
            response.add("messages", gson.toJsonTree(messages));
        } catch (Exception e) {
            logger.error("[GET_MESSAGES ERROR] Remote={} | ConversationId={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("conversationId") ? request.get("conversationId").getAsLong() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}

