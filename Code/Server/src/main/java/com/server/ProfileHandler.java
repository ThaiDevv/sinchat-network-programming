package com.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.config.Database;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProfileHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
    private final Gson gson = new Gson();

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
                String fullName = request.has("full_name") && !request.get("full_name").isJsonNull() ? request.get("full_name").getAsString() : null;
                String email = request.has("email") && !request.get("email").isJsonNull() ? request.get("email").getAsString() : null;
                String phoneNumber = request.has("phone_number") && !request.get("phone_number").isJsonNull() ? request.get("phone_number").getAsString() : null;
                String dateOfBirthStr = request.has("date_of_birth") && !request.get("date_of_birth").isJsonNull() ? request.get("date_of_birth").getAsString() : null;
                String avatar = request.has("avatar") && !request.get("avatar").isJsonNull() ? request.get("avatar").getAsString() : null;

                Date dateOfBirth = null;
                if (dateOfBirthStr != null && !dateOfBirthStr.trim().isEmpty()) {
                    dateOfBirth = Date.valueOf(dateOfBirthStr);
                }

                if (updateUserProfile(userId, fullName, email, phoneNumber, dateOfBirth, avatar)) {
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
        String query = "SELECT username, full_name, email, phone_number, date_of_birth, avatar FROM users WHERE id = ?";
        try (Connection c = getConnection(); PreparedStatement pstmt = c.prepareStatement(query)) {
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
            logger.error("DB error", e);
        }
        return null;
    }

    public boolean updateUserProfile(int userId, String fullName, String email, String phoneNumber, Date dateOfBirth, String avatar) {
        StringBuilder queryBuilder = new StringBuilder("UPDATE users SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (fullName != null) { queryBuilder.append("full_name = ?, "); params.add(fullName); }
        if (email != null) { queryBuilder.append("email = ?, "); params.add(email); }
        if (phoneNumber != null) { queryBuilder.append("phone_number = ?, "); params.add(phoneNumber); }
        if (dateOfBirth != null) { queryBuilder.append("date_of_birth = ?, "); params.add(dateOfBirth); }
        if (avatar != null) { queryBuilder.append("avatar = ?, "); params.add(avatar); }
        if (params.isEmpty()) return false;
        queryBuilder.setLength(queryBuilder.length() - 2);
        queryBuilder.append(" WHERE id = ?");
        params.add(userId);

        try (Connection c = getConnection(); PreparedStatement pstmt = c.prepareStatement(queryBuilder.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Date) pstmt.setDate(i + 1, (Date) param);
                else if (param instanceof String) pstmt.setString(i + 1, (String) param);
                else if (param instanceof Integer) pstmt.setInt(i + 1, (Integer) param);
            }
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("DB error", e);
        }
        return false;
    }
}
