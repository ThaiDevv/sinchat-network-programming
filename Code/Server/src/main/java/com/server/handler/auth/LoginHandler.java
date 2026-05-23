package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.model.User;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for the login endpoint.
 */
public class LoginHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginHandler.class);

    private final AuthService authService = new AuthService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {

        JsonObject response = new JsonObject();

        try {

            if (!request.has("username") || !request.has("password")) {
                response.addProperty("status", "error");
                response.addProperty("message",
                        "Missing username or password");

                return response;
            }

            String username =
                    request.get("username").getAsString();

            String password =
                    request.get("password").getAsString();

            User user = authService.login(username, password);

            if (user != null) {

                response.addProperty("status", "success");
                response.addProperty("userId", user.getId());
                response.addProperty("username", user.getUsername());

            } else {

                response.addProperty("status", "error");
                response.addProperty("message",
                        "Invalid username or password");
            }

        } catch (Exception e) {

            logger.error("Login processing error", e);

            response.addProperty("status", "error");
            response.addProperty("message",
                    "Internal Server Error");
        }

        return response;
    }
}