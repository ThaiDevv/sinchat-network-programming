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

public class RegisterHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
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
            String username = json.get("username").getAsString();
            String password = json.get("password").getAsString();
            String email = json.get("email").getAsString();

            try {
                // Gọi logic từ Service
                if (authService.register(username, password, email)) {
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Registration successful\"}");
                } else {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Registration failed\"}");
                }
            } catch (Exception dbEx) {
                logger.error("Registration error", dbEx);
                String errMsg = dbEx.getMessage() != null ? dbEx.getMessage().replace("\"", "'") : "Unknown Error";
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"" + errMsg + "\"}");
            }
        } catch (Exception e) {
            logger.error("Registration processing error", e);
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
