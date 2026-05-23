package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.config.Database;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SearchUserHandler {
    private static final Logger logger = LoggerFactory.getLogger(SearchUserHandler.class);

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("query")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing query parameter");
                return response;
            }

            String query = request.get("query").getAsString();
            long currentUserId = conn.getUserId() != null ? conn.getUserId() : -1;

            JsonArray users = new JsonArray();
            String sql = "SELECT id, username, avatar_url FROM users WHERE username LIKE ? AND id != ? LIMIT 15";

            try (Connection connection = Database.getConnection();
                 PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, "%" + query + "%");
                pstmt.setLong(2, currentUserId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject userObj = new JsonObject();
                        userObj.addProperty("userId", rs.getLong("id"));
                        userObj.addProperty("username", rs.getString("username"));
                        userObj.addProperty("avatarUrl", rs.getString("avatar_url"));
                        users.add(userObj);
                    }
                }
            }

            response.addProperty("status", "success");
            response.add("users", users);
        } catch (Exception e) {
            logger.error("Search user handler error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
