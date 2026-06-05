package com.server.handler;

import com.google.gson.JsonObject;
import com.server.model.User;
import com.server.repository.ConversationRepository;
import com.server.repository.UserRepository;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TypingHandler {
    private static final Logger logger = LoggerFactory.getLogger(TypingHandler.class);
    private final UserRepository userRepository = new UserRepository();
    private final ConversationRepository conversationRepository = new ConversationRepository();
    // Cache usernames to avoid DB query on every typing event
    private final java.util.concurrent.ConcurrentHashMap<Long, String> usernameCache = new java.util.concurrent.ConcurrentHashMap<>();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        if (!request.has("conversationId") || !request.has("isTyping")) {
            logger.warn("[TYPING] Remote={} | Missing required fields (conversationId or isTyping)",
                    conn.getRemoteAddress());
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Missing required fields for TYPING");
            return error;
        }

        Long fromUserId = conn.getUserId();
        if (fromUserId == null) {
            logger.warn("[TYPING] Remote={} | Not joined yet (userId=null), rejecting",
                    conn.getRemoteAddress());
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Not joined (missing userId). Send JOIN first");
            return error;
        }

        long conversationId = request.get("conversationId").getAsLong();
        boolean isTyping = request.get("isTyping").getAsBoolean();

        // Security check: verify user is a member of this conversation
        java.util.List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
        if (!memberIds.contains(fromUserId)) {
            logger.warn("[TYPING UNAUTHORIZED] Remote={} | UserId={} | ConversationId={} | user is not a member of this conversation",
                    conn.getRemoteAddress(), fromUserId, conversationId);
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Unauthorized: not a member of this conversation");
            return error;
        }

        logger.info("[TYPING] Remote={} | UserId={} | ConversationId={} | isTyping={}",
                conn.getRemoteAddress(), fromUserId, conversationId, isTyping);

        String fromUsername = usernameCache.computeIfAbsent(fromUserId, id -> {
            User user = userRepository.findById(id);
            return user != null ? user.getUsername() : "Ai đó";
        });

        JsonObject evt = new JsonObject();
        evt.addProperty("action", "TYPING_EVENT");
        evt.addProperty("conversationId", conversationId);
        evt.addProperty("userId", fromUserId);
        evt.addProperty("username", fromUsername);
        evt.addProperty("isTyping", isTyping);

        if (request.has("memberId")) {
            long memberId = request.get("memberId").getAsLong();
            logger.info("[TYPING BROADCAST] ConversationId={} | FromUserId={} | Broadcasting to memberId={}",
                    conversationId, fromUserId, memberId);
            TcpConnectionManager.getInstance().broadcastToUser(memberId, evt);
        } else {
            logger.info("[TYPING BROADCAST] ConversationId={} | FromUserId={} | Broadcasting to {} other members",
                    conversationId, fromUserId, memberIds.size() - 1);
            for (Long memberId : memberIds) {
                if (!memberId.equals(fromUserId)) {
                    TcpConnectionManager.getInstance().broadcastToUser(memberId, evt);
                }
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "Typing event broadcasted");
        return response;
    }
}
