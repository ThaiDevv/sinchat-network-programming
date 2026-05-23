package com.server;

import com.google.gson.JsonObject;
import com.server.config.Database;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.server.config.Database;

public class ProfileHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (request.has("subAction") && request.get("subAction").getAsString().equals("GET_PROFILE")) {
                if (!request.has("userId")) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Missing userId");
                    return response;
                }
                int userId = request.get("userId").getAsInt();
                JsonObject profile = getUserProfile(userId);
                if (profile != null) {
                    return profile;
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "User profile not found");
                }
            } else if (request.has("subAction") && request.get("subAction").getAsString().equals("UPDATE_PROFILE")) {
                if (!request.has("userId")) {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Missing userId");
                    return response;
                }
                int userId = request.get("userId").getAsInt();
                String email = request.has("email") && !request.get("email").isJsonNull() ? request.get("email").getAsString() : null;
                String avatarUrl = request.has("avatar_url") && !request.get("avatar_url").isJsonNull() ? request.get("avatar_url").getAsString() : null;

                if (updateUserProfile(userId, email, avatarUrl)) {
                    response.addProperty("status", "success");
                    response.addProperty("message", "Profile updated successfully");
                } else {
                    response.addProperty("status", "error");
                    response.addProperty("message", "Failed to update profile or no changes were made");
                }
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid or missing subAction");
            }
        } catch (Exception e) {
            logger.error("Profile handler error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    protected Connection getConnection() throws java.sql.SQLException {
        return Database.getConnection();
    }

    public JsonObject getUserProfile(int userId) {
        String query = "SELECT username, email, avatar_url FROM users WHERE id = ?";
        try (Connection c = getConnection(); PreparedStatement pstmt = c.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    JsonObject profile = new JsonObject();
                    profile.addProperty("status", "success");
                    profile.addProperty("username", rs.getString("username"));
                    profile.addProperty("email", rs.getString("email"));
                    profile.addProperty("avatar_url", rs.getString("avatar_url"));
                    return profile;
                }
            }
        } catch (Exception e) {
            logger.error("DB error", e);
        }
        return null;
    }

    public boolean updateUserProfile(int userId, String email, String avatarUrl) {
        StringBuilder queryBuilder = new StringBuilder("UPDATE users SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (email != null) { queryBuilder.append("email = ?, "); params.add(email); }
        if (avatarUrl != null) { queryBuilder.append("avatar_url = ?, "); params.add(avatarUrl); }
        if (params.isEmpty()) return false;
        queryBuilder.setLength(queryBuilder.length() - 2);
        queryBuilder.append(" WHERE id = ?");
        params.add(userId);

        try (Connection c = getConnection(); PreparedStatement pstmt = c.prepareStatement(queryBuilder.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) pstmt.setString(i + 1, (String) param);
                else if (param instanceof Integer) pstmt.setInt(i + 1, (Integer) param);
            }
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("DB error", e);
        }
        return false;
    }
}
