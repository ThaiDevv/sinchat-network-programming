package com.server;

import com.google.gson.JsonObject;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
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
                    logger.warn("[PROFILE GET] Remote={} | Missing userId for GET_PROFILE",
                            conn.getRemoteAddress());
                    response.addProperty("status", "error");
                    response.addProperty("message", "Missing userId");
                    return response;
                }
                long userId = request.get("userId").getAsLong();
                logger.info("[PROFILE GET] Remote={} | UserId={} | Fetching profile",
                        conn.getRemoteAddress(), userId);
                JsonObject profile = getUserProfile(userId);
                if (profile != null) {
                    logger.info("[PROFILE GET] Remote={} | UserId={} | Profile found",
                            conn.getRemoteAddress(), userId);
                    return profile;
                } else {
                    logger.warn("[PROFILE GET] Remote={} | UserId={} | Profile not found",
                            conn.getRemoteAddress(), userId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "User profile not found");
                }
            } else if (request.has("subAction") && request.get("subAction").getAsString().equals("UPDATE_PROFILE")) {
                if (!request.has("userId")) {
                    logger.warn("[PROFILE UPDATE] Remote={} | Missing userId for UPDATE_PROFILE",
                            conn.getRemoteAddress());
                    response.addProperty("status", "error");
                    response.addProperty("message", "Missing userId");
                    return response;
                }
                long userId = request.get("userId").getAsLong();
                String email = request.has("email") && !request.get("email").isJsonNull() ? request.get("email").getAsString() : null;
                String avatarUrl = request.has("avatar_url") && !request.get("avatar_url").isJsonNull() ? request.get("avatar_url").getAsString() : null;

                logger.info("[PROFILE UPDATE] Remote={} | UserId={} | email={} | avatar={} | Updating profile",
                        conn.getRemoteAddress(), userId, email, avatarUrl != null ? avatarUrl.substring(0, Math.min(30, avatarUrl.length())) + "..." : null);

                if (updateUserProfile(userId, email, avatarUrl)) {
                    logger.info("[PROFILE UPDATE] Remote={} | UserId={} | Profile updated successfully",
                            conn.getRemoteAddress(), userId);
                    response.addProperty("status", "success");
                    response.addProperty("message", "Profile updated successfully");
                } else {
                    logger.warn("[PROFILE UPDATE] Remote={} | UserId={} | Failed to update profile or no changes made",
                            conn.getRemoteAddress(), userId);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Failed to update profile or no changes were made");
                }
            } else {
                logger.warn("[PROFILE] Remote={} | Invalid or missing subAction: {}",
                        conn.getRemoteAddress(), request.has("subAction") ? request.get("subAction").getAsString() : "null");
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid or missing subAction");
            }
        } catch (Exception e) {
            logger.error("[PROFILE ERROR] Remote={} | Profile handler error: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    protected Connection getConnection() throws java.sql.SQLException {
        return Database.getConnection();
    }

    public JsonObject getUserProfile(long userId) {
        String query = "SELECT username, email, avatar_url FROM users WHERE id = ?";
        try (Connection c = getConnection(); PreparedStatement pstmt = c.prepareStatement(query)) {
            pstmt.setLong(1, userId);
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

    public boolean updateUserProfile(long userId, String email, String avatarUrl) {
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
                else if (param instanceof Long) pstmt.setLong(i + 1, (Long) param);
            }
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("DB error", e);
        }
        return false;
    }
}
