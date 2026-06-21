package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TCP handler for creating a new group conversation.
 *
 * Expected request fields:
 *   - creatorId  (long)       — the user creating the group
 *   - groupName  (string)     — name of the group
 *   - memberIds  (JsonArray)  — array of user IDs to add (must include creatorId or server will add automatically)
 */
public class CreateGroupHandler {
    private static final Logger logger = LoggerFactory.getLogger(CreateGroupHandler.class);
    private final ConversationService conversationService = new ConversationService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            // Validate required fields
            if (!request.has("creatorId") || !request.has("groupName") || !request.has("memberIds")) {
                logger.warn("[CREATE_GROUP] Remote={} | Missing required fields", conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing creatorId, groupName, or memberIds");
                return response;
            }

            long creatorId = request.get("creatorId").getAsLong();
            String groupName = request.get("groupName").getAsString().trim();

            // Security: ensure the requesting connection matches the creator
            Long connUserId = conn.getUserId();
            if (connUserId == null || connUserId != creatorId) {
                logger.warn("[CREATE_GROUP] Remote={} | ConnUserId={} | ClaimedCreatorId={} | Unauthorized",
                        conn.getRemoteAddress(), connUserId, creatorId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: creatorId mismatch");
                return response;
            }

            if (groupName.isEmpty()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Group name cannot be empty");
                return response;
            }
            if (groupName.length() > 100) {
                response.addProperty("status", "error");
                response.addProperty("message", "Group name too long (max 100 characters)");
                return response;
            }

            JsonArray memberIdsJson = request.getAsJsonArray("memberIds");
            if (memberIdsJson == null || memberIdsJson.size() == 0) {
                response.addProperty("status", "error");
                response.addProperty("message", "memberIds cannot be empty");
                return response;
            }

            List<Long> memberIds = new ArrayList<>();
            for (int i = 0; i < memberIdsJson.size(); i++) {
                memberIds.add(memberIdsJson.get(i).getAsLong());
            }

            // Always ensure creator is in the member list
            if (!memberIds.contains(creatorId)) {
                memberIds.add(0, creatorId);
            }

            if (memberIds.size() < 2) {
                response.addProperty("status", "error");
                response.addProperty("message", "A group must have at least 2 members");
                return response;
            }

            logger.info("[CREATE_GROUP] Remote={} | UserId={} | GroupName='{}' | Members={}",
                    conn.getRemoteAddress(), creatorId, groupName, memberIds);

            long conversationId = conversationService.createGroupConversation(creatorId, groupName, memberIds);

            logger.info("[CREATE_GROUP SUCCESS] Remote={} | UserId={} | GroupName='{}' | ConversationId={}",
                    conn.getRemoteAddress(), creatorId, groupName, conversationId);

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("groupName", groupName);

        } catch (Exception e) {
            logger.error("[CREATE_GROUP ERROR] Remote={} | Error: {}", conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
