package com.server.service;

import com.server.config.Database;
import com.server.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserService {

    public boolean registerUser(User user) {

        // 1. Kiểm tra username/email tồn tại chưa
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";

        try (
                Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)
        ) {

            checkStmt.setString(1, user.getUsername());
            checkStmt.setString(2, user.getEmail());

            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                return false;
            }

            // 2. Insert user mới
            String insertSql = """
                    INSERT INTO users(username, email, password, avatarURL)
                    VALUES (?, ?, ?, ?)
                    """;

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                insertStmt.setString(1, user.getUsername());
                insertStmt.setString(2, user.getEmail());
                insertStmt.setString(3, user.getPassword());
                insertStmt.setString(4, user.getAvatarUrl());

                int rowsAffected = insertStmt.executeUpdate();

                return rowsAffected > 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Method riêng để update avatar
    public boolean updateAvatar(long userId, String avatarUrl) {

        String sql = "UPDATE users SET avatarURL = ? WHERE id = ?";

        try (
                Connection conn = Database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, avatarUrl);
            stmt.setLong(2, userId);

            int rowsAffected = stmt.executeUpdate();

            return rowsAffected > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}