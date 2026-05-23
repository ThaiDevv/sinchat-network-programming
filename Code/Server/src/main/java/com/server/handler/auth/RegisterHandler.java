package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for the registration endpoint.
 */
public class RegisterHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
    private final AuthService authService = new AuthService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("username") || !request.has("password") || !request.has("email")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields: username, password, email");
                return response;
            }

            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();
            String email = request.get("email").getAsString();

            if (authService.register(username, password, email)) {
                response.addProperty("status", "success");
                response.addProperty("message", "Registration successful");
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Registration failed");
            }
        } catch (Exception dbEx) {
            logger.error("Registration error", dbEx);
            String errMsg = "Registration failed";
            if (dbEx.getMessage() != null && dbEx.getMessage().contains("Duplicate")) {
                errMsg = "Username or email already exists";
            }
            response.addProperty("status", "error");
            response.addProperty("message", errMsg);
        }
        return response;
    }
}
