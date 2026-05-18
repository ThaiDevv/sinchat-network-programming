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
                response.addProperty("status", "error");
                response.addProperty("message", "Missing userId or avatarUrl");
                return response;
            }

            int userId = request.get("userId").getAsInt();
            String avatarUrl = request.get("avatarUrl").getAsString();

            if (avatarService.changeAvatar(userId, avatarUrl)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Avatar updated successfully");
                response.addProperty("avatarUrl", avatarUrl);
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Failed to update avatar");
            }
        } catch (Exception e) {
            logger.error("Change avatar error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
