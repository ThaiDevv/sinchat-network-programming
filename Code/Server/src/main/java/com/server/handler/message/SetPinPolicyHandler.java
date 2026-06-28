package com.server.handler.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.server.repository.ConversationRepository;
import com.server.tcp.ClientConnection;

/**
 * TCP handler for toggling admin-only pin setting on a group conversation.
 *
 * Expected request fields:
 *   - conversationId (long)   — the group conversation
 *   - adminOnly      (boolean) — whether admin-only pinning is enabled
 *
 * Permission: only ADMIN or OWNER of the group may change this setting.
 */
public class SetPinPolicyHandler {
    private static final Logger logger = LoggerFactory.getLogger(SetPinPolicyHandler.class);
    private final ConversationRepository conversationRepository = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId") || !request.has("adminOnly")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or adminOnly");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            boolean adminOnly = request.get("adminOnly").getAsBoolean();
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            // Verify it's a GROUP conversation
            String convType = conversationRepository.getConversationType(conversationId);
            if (!"GROUP".equals(convType)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Pin policy can only be set on group conversations");
                return response;
            }

            // Permission check: only ADMIN or OWNER may change pin policy
            String role = conversationRepository.getUserRoleInConversation(conversationId, userId);
            if (!"ADMIN".equals(role) && !"OWNER".equals(role)) {
                logger.warn("[SET_PIN_POLICY] Remote={} | UserId={} | ConvId={} | Permission denied",
                        conn.getRemoteAddress(), userId, conversationId);
                response.addProperty("status", "error");
                response.addProperty("message", "Permission denied: only admins can change pin policy");
                return response;
            }

            // Update the setting
            conversationRepository.setAdminOnlyPin(conversationId, adminOnly);

            logger.info("[SET_PIN_POLICY] Remote={} | UserId={} | ConvId={} | adminOnly={}",
                    conn.getRemoteAddress(), userId, conversationId, adminOnly);

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("adminOnly", adminOnly);

        } catch (Exception e) {
            logger.error("[SET_PIN_POLICY ERROR] Remote={} | Error: {}", conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
