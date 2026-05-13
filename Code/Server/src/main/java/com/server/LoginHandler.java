package com.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoginHandler.class);
    private final Gson gson = new Gson();

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

            if (login(username, password)) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Login successful\"}");
            } else {
                sendResponse(exchange, 401, "{\"status\": \"error\", \"message\": \"Invalid username or password\"}");
            }
        } catch (Exception e) {
            logger.error("Login processing error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    /**
     * Server-side login function to verify credentials against the database.
     */
    public boolean login(String username, String password) {
        String query = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create(de.mkammerer.argon2.Argon2Factory.Argon2Types.ARGON2id);
                    return argon2.verify(storedHash, password.toCharArray());
                }
            }
        } catch (Exception e) {
            logger.error("Database authentication error", e);
        }
        return false;
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
