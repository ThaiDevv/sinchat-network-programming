package com.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class RegisterHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
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
            String dob = json.get("dob").getAsString();

            if (register(username, password, dob)) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Registration successful\"}");
            } else {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Registration failed. Username might already exist.\"}");
            }
        } catch (Exception e) {
            logger.error("Registration processing error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    public boolean register(String username, String password, String dob) {
        // Initialize Argon2id
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        
        // Hash the password
        // Parameters: iterations=10, memory=65536 KB, parallelism=1
        String hash = argon2.hash(10, 65536, 1, password.toCharArray());

        String query = "INSERT INTO users (username, password, dob) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hash);
            pstmt.setString(3, dob);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (Exception e) {
            logger.error("Database registration error", e);
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
