package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xu ly action REGISTER gui qua TCP socket.
 */
public class RegisterHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
    private final AuthService authService = new AuthService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("username") || !request.has("password") || !request.has("email")) {
                logger.warn("[REGISTER] Remote={} | Missing required fields (username, password, email)",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields: username, password, email");
                return response;
            }

            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();
            String email = request.get("email").getAsString();

            logger.info("[REGISTER ATTEMPT] Remote={} | Username={} | Email={} | Registration attempt",
                    conn.getRemoteAddress(), username, email);

            if (authService.register(username, password, email)) {
                logger.info("[REGISTER SUCCESS] Remote={} | Username={} | Email={} | Registration successful",
                        conn.getRemoteAddress(), username, email);
                response.addProperty("status", "success");
                response.addProperty("message", "Registration successful");
            } else {
                logger.warn("[REGISTER FAILED] Remote={} | Username={} | Email={} | Registration failed (service returned false)",
                        conn.getRemoteAddress(), username, email);
                response.addProperty("status", "error");
                response.addProperty("message", "Registration failed");
            }
        } catch (Exception dbEx) {
            logger.error("[REGISTER ERROR] Remote={} | Username={} | Email={} | Database error: {}",
                    conn.getRemoteAddress(),
                    request.has("username") ? request.get("username").getAsString() : "?",
                    request.has("email") ? request.get("email").getAsString() : "?",
                    dbEx.getMessage(), dbEx);
            String errMsg = "Registration failed";
            if (dbEx.getMessage() != null && dbEx.getMessage().contains("Duplicate")) {
                errMsg = "Username or email already exists";
                logger.warn("[REGISTER DUPLICATE] Remote={} | Duplicate username or email detected",
                        conn.getRemoteAddress());
            }
            response.addProperty("status", "error");
            response.addProperty("message", errMsg);
        }
        return response;
    }
}
