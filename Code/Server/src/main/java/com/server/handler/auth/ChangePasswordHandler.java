package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangePasswordHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChangePasswordHandler.class);
    private final AuthService authService = AuthService.getInstance();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("userId") || !request.has("oldPassword") || !request.has("newPassword")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields");
                return response;
            }

            long userId = request.get("userId").getAsLong();
            String oldPassword = request.get("oldPassword").getAsString();
            String newPassword = request.get("newPassword").getAsString();

            if (userId <= 0 || oldPassword.isBlank() || newPassword.isBlank()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid password change request");
                return response;
            }

            if (newPassword.length() < 6) {
                response.addProperty("status", "error");
                response.addProperty("message", "New password must be at least 6 characters");
                return response;
            }

            Long connectionUserId = conn.getUserId();
            if (connectionUserId != null && connectionUserId > 0 && connectionUserId != userId) {
                logger.warn("[CHANGE_PASSWORD] Remote={} | ConnUserId={} | RequestUserId={} | User mismatch",
                        conn.getRemoteAddress(), connectionUserId, userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized password change request");
                return response;
            }

            AuthService.ChangePasswordResult result =
                    authService.changePassword(userId, oldPassword, newPassword);

            switch (result) {
                case SUCCESS:
                    logger.info("[CHANGE_PASSWORD SUCCESS] Remote={} | UserId={}",
                            conn.getRemoteAddress(), userId);
                    response.addProperty("status", "success");
                    response.addProperty("message", "Password changed successfully");
                    break;
                case WRONG_OLD_PASSWORD:
                    logger.warn("[CHANGE_PASSWORD FAILED] Remote={} | UserId={} | Wrong old password",
                            conn.getRemoteAddress(), userId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Current password is incorrect");
                    break;
                case USER_NOT_FOUND:
                    response.addProperty("status", "error");
                    response.addProperty("message", "User not found");
                    break;
                default:
                    response.addProperty("status", "error");
                    response.addProperty("message", "Could not update password");
                    break;
            }
        } catch (Exception e) {
            logger.error("[CHANGE_PASSWORD ERROR] Remote={} | Error: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
