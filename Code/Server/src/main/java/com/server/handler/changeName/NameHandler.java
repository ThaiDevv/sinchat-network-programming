package com.server.handler.changeName;

import com.google.gson.JsonObject;
import com.server.service.UserNameService;
import com.server.tcp.ClientConnection;
import com.server.tcp.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameHandler {
    private static final Logger logger = LoggerFactory.getLogger(NameHandler.class);
    private final UserNameService userNameService = new UserNameService();

    public JsonObject handle(ClientConnection conn, JsonObject request) {

        JsonObject response = new JsonObject();

        try {
            // 1. Validate request parameters
            if (!request.has("userId") || !request.has("newUsername")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Thiếu userId hoặc newUsername");
                return response;
            }

            long userId = request.get("userId").getAsLong();
            String newUsername = request.get("newUsername").getAsString();

            // 2. Security: verify userId matches the authenticated connection
            Long connUserId = conn.getUserId();
            if (connUserId == null || connUserId.longValue() != userId) {
                logger.warn("[CHANGE_NAME] Remote={} | ConnUserId={} | RequestedUserId={} | Unauthorized",
                        conn.getRemoteAddress(), connUserId, userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized: userId mismatch");
                return response;
            }

            logger.info("[CHANGE_NAME] Remote={} | UserId={} | Attempting to change username to '{}'",
                    conn.getRemoteAddress(), userId, newUsername);

            // 3. Gọi Service layer để xử lý logic
            if (userNameService.updateUsername(userId, newUsername)) {
                logger.info("[CHANGE_NAME] Remote={} | UserId={} | Username changed successfully to '{}'",
                        conn.getRemoteAddress(), userId, newUsername);
                response.addProperty("status", "success");
                response.addProperty("message", "Username changed successfully");
                response.addProperty("newUsername", newUsername);

                // Broadcast changes to peers ASYNCHRONOUSLY
                // để không chặn response gửi về client (tránh timeout 10s)
                final long broadcastUserId = userId;
                final String broadcastNewUsername = newUsername;
                Thread.startVirtualThread(() -> {
                    try {
                        PresenceService.getInstance().broadcastNameChangeToPeers(broadcastUserId, broadcastNewUsername);
                    } catch (Exception ex) {
                        logger.error("[CHANGE_NAME BROADCAST ERROR] UserId={} | Error: {}",
                                broadcastUserId, ex.getMessage(), ex);
                    }
                });
            } else {
                logger.warn("[CHANGE_NAME] Remote={} | UserId={} | Failed to update username",
                        conn.getRemoteAddress(), userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Không thể đổi username (trùng hoặc lỗi hệ thống)");
            }

        } catch (Exception e) {
            logger.error("[CHANGE_NAME ERROR] Remote={} | Error: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }

        return response;
    }
}
