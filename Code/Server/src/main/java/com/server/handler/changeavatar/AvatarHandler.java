package com.server.handler.changeavatar;

import com.google.gson.JsonObject;
import com.server.service.AvatarService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvatarHandler {
    private static final Logger logger = LoggerFactory.getLogger(AvatarHandler.class);
    private final AvatarService avatarService = new AvatarService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("userId") || !request.has("avatarUrl")) {
                logger.warn("[CHANGE_AVATAR] Remote={} | Missing userId or avatarUrl",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing userId or avatarUrl");
                return response;
            }

            long userId = request.get("userId").getAsLong();
            String avatarUrl = request.get("avatarUrl").getAsString();

            // Security: verify userId matches the authenticated connection
            Long connUserId = conn.getUserId();
            if (connUserId == null || connUserId != userId) {
                logger.warn("[CHANGE_AVATAR] Remote={} | ConnUserId={} | RequestedUserId={} | Unauthorized",
                        conn.getRemoteAddress(), connUserId, userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: userId mismatch");
                return response;
            }

            logger.info("[CHANGE_AVATAR] Remote={} | UserId={} | avatarUrl={} | Attempting avatar change",
                    conn.getRemoteAddress(), userId,
                    avatarUrl.substring(0, Math.min(30, avatarUrl.length())) + "...");

            if (avatarService.changeAvatar(userId, avatarUrl)) {
                logger.info("[CHANGE_AVATAR] Remote={} | UserId={} | Avatar updated successfully",
                        conn.getRemoteAddress(), userId);
                response.addProperty("status", "success");
                response.addProperty("message", "Avatar updated successfully");
                response.addProperty("avatarUrl", avatarUrl);
                
                // Broadcast to friends and peers (userId only, peers will fetch themselves)
                com.server.tcp.PresenceService.getInstance().broadcastAvatarChangeToPeers(userId, null);
            } else {
                logger.warn("[CHANGE_AVATAR] Remote={} | UserId={} | Failed to update avatar",
                        conn.getRemoteAddress(), userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Failed to update avatar");
            }
        } catch (Exception e) {
            logger.error("[CHANGE_AVATAR ERROR] Remote={} | UserId={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("userId") ? request.get("userId").getAsLong() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
