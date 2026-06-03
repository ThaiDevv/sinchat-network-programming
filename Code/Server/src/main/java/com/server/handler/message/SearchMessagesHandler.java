package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.model.MessageSearchResult;
import com.server.repository.ConversationRepository;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchMessagesHandler {
    private static final Logger logger = LoggerFactory.getLogger(SearchMessagesHandler.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final Gson gson = new Gson();
    private final MessageService messageService = new MessageService();
    private final ConversationRepository conversationRepository = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("keyword")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or keyword");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            String keyword = request.get("keyword").getAsString().trim();
            int limit = request.has("limit") ? request.get("limit").getAsInt() : DEFAULT_LIMIT;
            int offset = request.has("offset") ? request.get("offset").getAsInt() : 0;

            if (conversationId <= 0 || keyword.isBlank()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid message search request");
                return response;
            }

            limit = Math.max(1, Math.min(limit, MAX_LIMIT));
            offset = Math.max(0, offset);

            Long userId = conn.getUserId();
            if (userId == null || !conversationRepository.getMemberIds(conversationId).contains(userId)) {
                logger.warn("[SEARCH_MESSAGES] Remote={} | UserId={} | ConversationId={} | Unauthorized search",
                        conn.getRemoteAddress(), userId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized message search request");
                return response;
            }

            // Tra kem username de UI khong phai doan ten user tu senderId.
            List<MessageSearchResult> messages = messageService.searchMessages(conversationId, keyword, limit, offset);
            logger.info("[SEARCH_MESSAGES] Remote={} | UserId={} | ConversationId={} | KeywordLength={} | Count={}",
                    conn.getRemoteAddress(), userId, conversationId, keyword.length(), messages.size());

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("keyword", keyword);
            response.addProperty("count", messages.size());
            response.add("messages", gson.toJsonTree(messages));
        } catch (Exception e) {
            logger.error("[SEARCH_MESSAGES ERROR] Remote={} | Error: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
