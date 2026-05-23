package com.server.service;

import com.server.config.Database;
import com.server.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserService {

    public boolean registerUser(User user) {
        // 1. Kiểm tra xem username hoặc email đã tồn tại trong DB chưa
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, user.getUsername());
            checkStmt.setString(2, user.getEmail());
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Nếu tìm thấy kết quả nghĩa là user hoặc email đã tồn tại
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String updateAvatarURL = "Update users set avatarURL = ? where id =?";

        try (Connection conn = Database.getConnection();
                PreparedStatement updateAvatarStmt = conn.prepareStatement(updateAvatarURL)) {
            updateAvatarStmt.setString(1, user.getAvatarUrl());
            updateAvatarStmt.setLong(2, user.getId());
            int rowsAffected = updateAvatarStmt.executeUpdate();
            return rowsAffected > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
