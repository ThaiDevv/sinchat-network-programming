package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for the forgot‑password endpoint.
 */
public class ForgotPasswordHandler {
    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordHandler.class);
    private final AuthService authService = new AuthService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            // Request for a reset code (username only)
            if (request.has("username") && !request.has("code")) {
                String username = request.get("username").getAsString();
                if (username == null || username.trim().isEmpty()) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Invalid username");
                    return response;
                }
                String code = authService.generateResetCode(username);
                if (code != null) {
                    response.addProperty("status", "success");
                    response.addProperty("message", "Reset code generated.");
                    response.addProperty("code", code);
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Account not found");
                }
                return response;
            }

            // Request to reset the password (code + new password)
            if (request.has("code") && request.has("password")) {
                String code = request.get("code").getAsString();
                String password = request.get("password").getAsString();

                if (authService.resetPassword(code, password)) {
                    response.addProperty("status", "success");
                    response.addProperty("message", "Password reset successful");
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Invalid or expired code");
                }
                return response;
            }

            response.addProperty("status", "error");
            response.addProperty("message", "Missing required info (username or code/password)");
        } catch (Exception e) {
            logger.error("Error in forgot password handler", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
