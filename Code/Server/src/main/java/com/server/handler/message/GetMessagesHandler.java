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
                    conn.getRemoteAddress(), conn.getUserId(), conversationId, limit, offset);

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
