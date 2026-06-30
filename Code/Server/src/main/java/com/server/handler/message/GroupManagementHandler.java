package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TCP handler for group management operations.
 *
 * Expected request fields:
 *   - conversationId (long)   — the group conversation ID
 *   - subAction      (string) — one of: GET_MEMBERS, RENAME, ADD_MEMBER, KICK_MEMBER, TRANSFER_ADMIN, DISBAND
 *
 * Additional fields per subAction:
 *   - RENAME:         newName (string)
 *   - ADD_MEMBER:     targetUserId (long)
 *   - KICK_MEMBER:    targetUserId (long)
 *   - TRANSFER_ADMIN: targetUserId (long)
 */
public class GroupManagementHandler {
    private static final Logger logger = LoggerFactory.getLogger(GroupManagementHandler.class);
    private final ConversationService conversationService = new ConversationService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            // Validate required fields
            if (!request.has("conversationId") || !request.has("subAction")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId or subAction");
                return response;
            }

            long conversationId = request.get("conversationId").getAsLong();
            String subAction = request.get("subAction").getAsString();

            // Verify user is authenticated
            Long connUserId = conn.getUserId();
            if (connUserId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Not authenticated");
                return response;
            }

            // Verify conversation is a GROUP
            String type = conversationService.getConversationType(conversationId);
            if (type == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Conversation not found");
                return response;
            }
            if (!"GROUP".equals(type)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Not a group conversation");
                return response;
            }

            // Verify user is a member
            if (!conversationService.isGroupMember(conversationId, connUserId)) {
                response.addProperty("status", "error");
                response.addProperty("message", "You are not a member of this group");
                return response;
            }

            logger.info("[MANAGE_GROUP] Remote={} | UserId={} | ConvId={} | SubAction={}",
                    conn.getRemoteAddress(), connUserId, conversationId, subAction);

            switch (subAction) {
                case "GET_MEMBERS":
                    return handleGetMembers(conversationId, connUserId, response);
                case "RENAME":
                    return handleRename(request, conversationId, connUserId, response);
                case "ADD_MEMBER":
                    return handleAddMember(request, conversationId, connUserId, response);
                case "KICK_MEMBER":
                    return handleKickMember(request, conversationId, connUserId, response);
                case "TRANSFER_ADMIN":
                    return handleTransferAdmin(request, conversationId, connUserId, response);
                case "DISBAND":
                    return handleDisband(conversationId, connUserId, response);
                default:
                    response.addProperty("status", "error");
                    response.addProperty("message", "Unknown subAction: " + subAction);
                    return response;
            }
        } catch (Exception e) {
            logger.error("[MANAGE_GROUP ERROR] Remote={} | Error: {}", conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    // ---- GET_MEMBERS ----
    private JsonObject handleGetMembers(long conversationId, long connUserId, JsonObject response) {
        JsonArray members = conversationService.getGroupMembers(conversationId);
        String role = conversationService.getMemberRole(conversationId, connUserId);
        String groupName = conversationService.getConversationName(conversationId);
        long createdBy = conversationService.getConversationCreator(conversationId);

        response.addProperty("status", "success");
        response.add("members", members);
        response.addProperty("myRole", role != null ? role : "MEMBER");
        response.addProperty("groupName", groupName != null ? groupName : "");
        response.addProperty("conversationId", conversationId);
        response.addProperty("createdBy", createdBy);
        return response;
    }

    // ---- RENAME ----
    private JsonObject handleRename(JsonObject request, long conversationId, long connUserId, JsonObject response) throws Exception {
        // Any member can rename the group
        if (!conversationService.isGroupMember(conversationId, connUserId)) {
            response.addProperty("status", "error");
            response.addProperty("message", "You are not a member of this group");
            return response;
        }

        if (!request.has("newName")) {
            response.addProperty("status", "error");
            response.addProperty("message", "Missing newName");
            return response;
        }

        String newName = request.get("newName").getAsString().trim();
        if (newName.isEmpty()) {
            response.addProperty("status", "error");
            response.addProperty("message", "Group name cannot be empty");
            return response;
        }
        if (newName.length() > 100) {
            response.addProperty("status", "error");
            response.addProperty("message", "Group name too long (max 100 characters)");
            return response;
        }

        conversationService.renameGroup(conversationId, newName);
        logger.info("[MANAGE_GROUP RENAME] ConvId={} | NewName='{}' | By UserId={}", conversationId, newName, connUserId);

        response.addProperty("status", "success");
        response.addProperty("conversationId", conversationId);
        response.addProperty("newName", newName);

        // Broadcast to all group members
        broadcastToGroup(conversationId, "GROUP_RENAMED", json -> {
            json.addProperty("conversationId", conversationId);
            json.addProperty("newName", newName);
            json.addProperty("renamedBy", connUserId);
        });

        return response;
    }

    // ---- ADD_MEMBER ----
    private JsonObject handleAddMember(JsonObject request, long conversationId, long connUserId, JsonObject response) throws Exception {
        // Any member can add new members
        if (!conversationService.isGroupMember(conversationId, connUserId)) {
            response.addProperty("status", "error");
            response.addProperty("message", "You are not a member of this group");
            return response;
        }

        if (!request.has("targetUserId")) {
            response.addProperty("status", "error");
            response.addProperty("message", "Missing targetUserId");
            return response;
        }

        long targetUserId = request.get("targetUserId").getAsLong();

        // Check if already a member
        if (conversationService.isGroupMember(conversationId, targetUserId)) {
            response.addProperty("status", "error");
            response.addProperty("message", "User is already a member of this group");
            return response;
        }

        conversationService.addGroupMember(conversationId, targetUserId);
        logger.info("[MANAGE_GROUP ADD_MEMBER] ConvId={} | AddedUserId={} | By UserId={}", conversationId, targetUserId, connUserId);

        response.addProperty("status", "success");
        response.addProperty("conversationId", conversationId);
        response.addProperty("addedUserId", targetUserId);

        // Broadcast to all group members (including the newly added one)
        broadcastToGroup(conversationId, "MEMBER_ADDED", json -> {
            json.addProperty("conversationId", conversationId);
            json.addProperty("addedUserId", targetUserId);
            json.addProperty("addedBy", connUserId);
        });

        return response;
    }

    // ---- KICK_MEMBER ----
    private JsonObject handleKickMember(JsonObject request, long conversationId, long connUserId, JsonObject response) throws Exception {
        // Only the original creator can kick members
        long creatorId = conversationService.getConversationCreator(conversationId);
        if (connUserId != creatorId) {
            response.addProperty("status", "error");
            response.addProperty("message", "Only the group creator can kick members");
            return response;
        }

        if (!request.has("targetUserId")) {
            response.addProperty("status", "error");
            response.addProperty("message", "Missing targetUserId");
            return response;
        }

        long targetUserId = request.get("targetUserId").getAsLong();

        // Cannot kick yourself
        if (targetUserId == connUserId) {
            response.addProperty("status", "error");
            response.addProperty("message", "Cannot kick yourself. Use Leave Group instead.");
            return response;
        }

        // Verify target is a member
        if (!conversationService.isGroupMember(conversationId, targetUserId)) {
            response.addProperty("status", "error");
            response.addProperty("message", "User is not a member of this group");
            return response;
        }

        // Get member list before kicking (to broadcast to all including the kicked user)
        List<Long> memberIds = conversationService.getMemberIds(conversationId);

        conversationService.kickGroupMember(conversationId, targetUserId);
        logger.info("[MANAGE_GROUP KICK_MEMBER] ConvId={} | KickedUserId={} | By UserId={}", conversationId, targetUserId, connUserId);

        response.addProperty("status", "success");
        response.addProperty("conversationId", conversationId);
        response.addProperty("kickedUserId", targetUserId);

        // Broadcast to all members (including the kicked user so they can react)
        JsonObject broadcastMsg = new JsonObject();
        broadcastMsg.addProperty("action", "MEMBER_KICKED");
        broadcastMsg.addProperty("conversationId", conversationId);
        broadcastMsg.addProperty("kickedUserId", targetUserId);
        broadcastMsg.addProperty("kickedBy", connUserId);

        for (Long memberId : memberIds) {
            TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
        }

        return response;
    }

    // ---- TRANSFER_ADMIN ----
    private JsonObject handleTransferAdmin(JsonObject request, long conversationId, long connUserId, JsonObject response) throws Exception {
        // Only the original creator can transfer admin rights
        long creatorId = conversationService.getConversationCreator(conversationId);
        if (connUserId != creatorId) {
            response.addProperty("status", "error");
            response.addProperty("message", "Only the group creator can transfer admin rights");
            return response;
        }

        if (!request.has("targetUserId")) {
            response.addProperty("status", "error");
            response.addProperty("message", "Missing targetUserId");
            return response;
        }

        long targetUserId = request.get("targetUserId").getAsLong();

        if (targetUserId == connUserId) {
            response.addProperty("status", "error");
            response.addProperty("message", "You are already the admin");
            return response;
        }

        // Verify target is a member
        if (!conversationService.isGroupMember(conversationId, targetUserId)) {
            response.addProperty("status", "error");
            response.addProperty("message", "Target user is not a member of this group");
            return response;
        }

        conversationService.transferGroupAdmin(conversationId, connUserId, targetUserId);
        logger.info("[MANAGE_GROUP TRANSFER_ADMIN] ConvId={} | FromUserId={} | ToUserId={}", conversationId, connUserId, targetUserId);

        response.addProperty("status", "success");
        response.addProperty("conversationId", conversationId);
        response.addProperty("newAdminId", targetUserId);
        response.addProperty("oldAdminId", connUserId);

        // Broadcast to all group members
        broadcastToGroup(conversationId, "ADMIN_TRANSFERRED", json -> {
            json.addProperty("conversationId", conversationId);
            json.addProperty("newAdminId", targetUserId);
            json.addProperty("oldAdminId", connUserId);
        });

        return response;
    }

    // ---- DISBAND ----
    private JsonObject handleDisband(long conversationId, long connUserId, JsonObject response) throws Exception {
        // Only the original creator can disband the group
        long creatorId = conversationService.getConversationCreator(conversationId);
        if (connUserId != creatorId) {
            response.addProperty("status", "error");
            response.addProperty("message", "Only the group creator can disband the group");
            return response;
        }

        // Get member list before disbanding (to broadcast)
        List<Long> memberIds = conversationService.getMemberIds(conversationId);
        String groupName = conversationService.getConversationName(conversationId);

        conversationService.disbandGroup(conversationId);
        logger.info("[MANAGE_GROUP DISBAND] ConvId={} | By UserId={}", conversationId, connUserId);

        response.addProperty("status", "success");
        response.addProperty("conversationId", conversationId);

        // Broadcast to all former members
        JsonObject broadcastMsg = new JsonObject();
        broadcastMsg.addProperty("action", "GROUP_DISBANDED");
        broadcastMsg.addProperty("conversationId", conversationId);
        broadcastMsg.addProperty("disbandedBy", connUserId);
        broadcastMsg.addProperty("groupName", groupName != null ? groupName : "");

        for (Long memberId : memberIds) {
            TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
        }

        return response;
    }

    // ---- BROADCAST HELPER ----
    private void broadcastToGroup(long conversationId, String action, java.util.function.Consumer<JsonObject> customize) {
        List<Long> memberIds = conversationService.getMemberIds(conversationId);
        JsonObject msg = new JsonObject();
        msg.addProperty("action", action);
        customize.accept(msg);

        for (Long memberId : memberIds) {
            TcpConnectionManager.getInstance().broadcastToUser(memberId, msg);
        }
    }
}
