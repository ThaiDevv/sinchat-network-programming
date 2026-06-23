package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TCP handler for a user leaving a group conversation.
 *
 * Expected request fields:
 *   - conversationId (long) — the ID of the group conversation to leave
 *   - userId         (long) — the ID of the user who is leaving
 */
public class LeaveGroupHandler {
    private static final Logger logger = LoggerFactory.getLogger(LeaveGroupHandler.class);
    private final ConversationService conversationService = new ConversationService();
    private final com.server.repository.ConversationRepository conversationRepository = new com.server.repository.ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("userId")) {
                logger.warn("[LEAVE_GROUP] Remote={} | Missing conversationId or userId", conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or userId");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            long userId = request.get("userId").getAsLong();

            // Security: ensure requesting connection matches the user leaving
            Long connUserId = conn.getUserId();
            if (connUserId == null || connUserId != userId) {
                logger.warn("[LEAVE_GROUP] Remote={} | ConnUserId={} | ClaimedUserId={} | Unauthorized",
                        conn.getRemoteAddress(), connUserId, userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: userId mismatch");
                return response;
            }

            // Verify conversation type is GROUP
            String type = conversationService.getConversationType(conversationId);
            if (type == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Conversation not found");
                return response;
            }
            if (!"GROUP".equals(type)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Cannot leave a private conversation");
                return response;
            }

            // Verify user is a member of the group
            List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            if (!memberIds.contains(userId)) {
                response.addProperty("status", "error");
                response.addProperty("message", "You are not a member of this conversation");
                return response;
            }

            logger.info("[LEAVE_GROUP] Remote={} | UserId={} | Leaving group conversationId={}",
                    conn.getRemoteAddress(), userId, conversationId);

            // Perform leaving group
            conversationService.leaveGroupConversation(conversationId, userId);

            logger.info("[LEAVE_GROUP SUCCESS] Remote={} | UserId={} | Left conversationId={}",
                    conn.getRemoteAddress(), userId, conversationId);

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("userId", userId);

            // Broadcast LEFT_GROUP event to other group members
            JsonObject broadcastMsg = new JsonObject();
            broadcastMsg.addProperty("action", "LEFT_GROUP");
            broadcastMsg.addProperty("conversationId", conversationId);
            broadcastMsg.addProperty("userId", userId);

            for (Long memberId : memberIds) {
                if (memberId != userId) {
                    logger.info("[LEAVE_GROUP BROADCAST] LeftUserId={} | Broadcasting to memberId={}",
                            userId, memberId);
                    TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
                }
            }

        } catch (Exception e) {
            logger.error("[LEAVE_GROUP ERROR] Remote={} | Error: {}", conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
