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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProfileHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "https://yourdomain.com");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // Handle GET request to view profile
        if ("GET".equalsIgnoreCase(method)) {
            handleGetProfile(exchange);
        } 
        // Handle POST request to update profile
        else if ("POST".equalsIgnoreCase(method)) {
            handleUpdateProfile(exchange);
        } 
        // If neither GET nor POST, return 405 Method Not Allowed
        else {
            sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
        }
    }

    private boolean validateToken(HttpExchange exchange, int requestedUserId) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                JsonObject json = gson.fromJson(payload, JsonObject.class);
                if (json.has("userId")) {
                    return json.get("userId").getAsInt() == requestedUserId;
                }
            }
            if (token.contains(String.valueOf(requestedUserId))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts userId from the query string and returns the user's profile.
     */
    private void handleGetProfile(HttpExchange exchange) throws IOException {
        // Extract userId from query parameters e.g., /profile?userId=1
        String query = exchange.getRequestURI().getQuery();
        int userId = -1;
        
        if (query != null && query.contains("userId=")) {
            try {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("userId=")) {
                        userId = Integer.parseInt(param.split("=")[1]);
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                logger.error("Invalid userId format in GET request", e);
            }
        }

        // If userId is not provided or invalid, send 400 Bad Request
        if (userId == -1) {
            sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Missing or invalid userId parameter\"}");
            return;
        }

        if (!validateToken(exchange, userId)) {
            sendResponse(exchange, 401, "{\"status\": \"error\", \"message\": \"Unauthorized\"}");
            return;
        }

        // Fetch the profile
        JsonObject profile = getUserProfile(userId);
        if (profile != null) {
            sendResponse(exchange, 200, gson.toJson(profile));
        } else {
            sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"User profile not found\"}");
        }
    }

    /**
     * Parses the JSON request body and updates the user's profile.
     */
    private void handleUpdateProfile(HttpExchange exchange) throws IOException {
        String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthStr != null) {
            try {
                long contentLength = Long.parseLong(contentLengthStr);
                if (contentLength > 10240) {
                    sendResponse(exchange, 413, "{\"status\": \"error\", \"message\": \"Request body too large\"}");
                    return;
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            
            // The request must contain the userId to identify whose profile to update
            if (!json.has("userId")) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Missing userId\"}");
                return;
            }

            int userId = json.get("userId").getAsInt();

            if (!validateToken(exchange, userId)) {
                sendResponse(exchange, 401, "{\"status\": \"error\", \"message\": \"Unauthorized\"}");
                return;
            }
            
            // Extract profile fields (allow nulls if the user doesn't want to update them all)
            String fullName = json.has("full_name") && !json.get("full_name").isJsonNull() ? json.get("full_name").getAsString() : null;
            String email = json.has("email") && !json.get("email").isJsonNull() ? json.get("email").getAsString() : null;
            String phoneNumber = json.has("phone_number") && !json.get("phone_number").isJsonNull() ? json.get("phone_number").getAsString() : null;
            String dateOfBirthStr = json.has("date_of_birth") && !json.get("date_of_birth").isJsonNull() ? json.get("date_of_birth").getAsString() : null;
            String avatar = json.has("avatar") && !json.get("avatar").isJsonNull() ? json.get("avatar").getAsString() : null;

            if (fullName == null && email == null && phoneNumber == null && dateOfBirthStr == null && avatar == null) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"No fields to update\"}");
                return;
            }

            if (fullName != null) {
                if (fullName.trim().isEmpty() || fullName.length() > 100) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid full_name\"}");
                    return;
                }
            }
            if (email != null) {
                if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid email format\"}");
                    return;
                }
            }
            if (phoneNumber != null) {
                if (!phoneNumber.matches("^\\+?[0-9]{7,15}$")) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid phone_number format\"}");
                    return;
                }
            }
            if (avatar != null) {
                if (!(avatar.startsWith("https://") || avatar.startsWith("/avatars/")) || avatar.length() > 500) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid avatar\"}");
                    return;
                }
            }

            Date dateOfBirth = null;
            if (dateOfBirthStr != null && !dateOfBirthStr.trim().isEmpty()) {
                try {
                    dateOfBirth = Date.valueOf(dateOfBirthStr); // Expected format: YYYY-MM-DD
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid date format. Use YYYY-MM-DD.\"}");
                    return;
                }
            }

            // Update the profile in the database
            if (updateUserProfile(userId, fullName, email, phoneNumber, dateOfBirth, avatar)) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Profile updated successfully\"}");
            } else {
                sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Failed to update profile or no changes were made\"}");
            }
        } catch (Exception e) {
            logger.error("Profile update error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    /**
     * Retrieves the user profile from the database.
     * @param userId The ID of the user.
     * @return JsonObject containing the user's profile, or null if not found.
     */
    public JsonObject getUserProfile(int userId) {
        // SQL query to select user profile details
        String query = "SELECT username, full_name, email, phone_number, date_of_birth, avatar FROM users WHERE id = ?";
        
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    JsonObject profile = new JsonObject();
                    profile.addProperty("status", "success");
                    profile.addProperty("username", rs.getString("username"));
                    profile.addProperty("full_name", rs.getString("full_name"));
                    profile.addProperty("email", rs.getString("email"));
                    profile.addProperty("phone_number", rs.getString("phone_number"));
                    
                    Date dob = rs.getDate("date_of_birth");
                    profile.addProperty("date_of_birth", dob != null ? dob.toString() : null);
                    
                    profile.addProperty("avatar", rs.getString("avatar"));
                    
                    return profile;
                }
            }
        } catch (Exception e) {
            logger.error("Database error while fetching user profile for userId: [REDACTED]", e);
        }
        return null;
    }

    /**
     * Updates the user profile in the database.
     * @param userId The ID of the user.
     * @param fullName The full name of the user.
     * @param email The email of the user.
     * @param phoneNumber The phone number of the user.
     * @param dateOfBirth The date of birth of the user.
     * @param avatar The avatar path or URL of the user.
     * @return true if update was successful, false otherwise.
     */
    public boolean updateUserProfile(int userId, String fullName, String email, String phoneNumber, Date dateOfBirth, String avatar) {
        StringBuilder queryBuilder = new StringBuilder("UPDATE users SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        
        if (fullName != null) { queryBuilder.append("full_name = ?, "); params.add(fullName); }
        if (email != null) { queryBuilder.append("email = ?, "); params.add(email); }
        if (phoneNumber != null) { queryBuilder.append("phone_number = ?, "); params.add(phoneNumber); }
        if (dateOfBirth != null) { queryBuilder.append("date_of_birth = ?, "); params.add(dateOfBirth); }
        if (avatar != null) { queryBuilder.append("avatar = ?, "); params.add(avatar); }
        
        if (params.isEmpty()) {
            return false;
        }
        
        // Remove trailing ", "
        queryBuilder.setLength(queryBuilder.length() - 2);
        queryBuilder.append(" WHERE id = ?");
        params.add(userId);
        
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(queryBuilder.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Date) {
                    pstmt.setDate(i + 1, (Date) param);
                } else if (param instanceof String) {
                    pstmt.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                }
            }
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (Exception e) {
            logger.error("Database error while updating user profile for userId: [REDACTED]", e);
        }
        return false;
    }

    /**
     * Helper method to send HTTP response back to the client.
     */
    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "https://yourdomain.com");
        byte[] response = body.getBytes();
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
