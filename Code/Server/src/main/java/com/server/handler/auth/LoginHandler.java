package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.model.User;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xu ly action LOGIN gui qua TCP socket.
 */
public class LoginHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginHandler.class);

    private final AuthService authService = new AuthService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {

        JsonObject response = new JsonObject();

        try {

            if (!request.has("username") || !request.has("password")) {
                logger.warn("[LOGIN] Remote={} | Missing username or password in request",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message",
                        "Missing username or password");

                return response;
            }

            String username =
                    request.get("username").getAsString();

            String password =
                    request.get("password").getAsString();

            logger.info("[LOGIN ATTEMPT] Remote={} | Username={} | Login attempt",
                    conn.getRemoteAddress(), username);

            User user = authService.login(username, password);

            if (user != null) {
                logger.info("[LOGIN SUCCESS] Remote={} | Username={} | UserId={} | Login successful",
                        conn.getRemoteAddress(), username, user.getId());
                response.addProperty("status", "success");
                response.addProperty("userId", user.getId());
                response.addProperty("username", user.getUsername());

            } else {
                logger.warn("[LOGIN FAILED] Remote={} | Username={} | Invalid credentials",
                        conn.getRemoteAddress(), username);
                response.addProperty("status", "error");
                response.addProperty("message",
                        "Invalid username or password");
            }

        } catch (Exception e) {
            logger.error("[LOGIN ERROR] Remote={} | Error during login processing: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);

            response.addProperty("status", "error");
            response.addProperty("message",
                    "Internal Server Error");
        }

        return response;
    }
}
