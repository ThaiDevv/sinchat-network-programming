package com.server.handler.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class LoginHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoginHandler.class);
    private final Gson gson = new Gson();
    private final AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            if (json == null || !json.has("username") || !json.has("password")) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Missing username or password\"}");
                return;
            }

            String username = json.get("username").getAsString();
            String password = json.get("password").getAsString();

            // Gọi logic từ Service
            com.server.model.User user = authService.login(username, password);
            if (user != null) {
                String body = String.format(
                    "{\"status\": \"success\", \"userId\": %d, \"username\": \"%s\"}",
                    user.getId(), user.getUsername()
                );
                sendResponse(exchange, 200, body);
            } else {
                sendResponse(exchange, 401, "{\"status\": \"error\", \"message\": \"Invalid username or password\"}");
            }
        } catch (Exception e) {
            logger.error("Login processing error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] response = body.getBytes();
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
