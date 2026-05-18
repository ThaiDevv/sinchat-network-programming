package com.server.service;

import com.server.config.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AvatarService {
    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);

    public boolean changeAvatar(int userId, String avatarUrl) {
        String updateAvatar = "UPDATE users SET avatar_url = ? WHERE id = ?";

        try (Connection conn = Database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(updateAvatar)) {

            stmt.setString(1, avatarUrl);
            stmt.setInt(2, userId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0; // false nếu user không tồn tại

        } catch (Exception e) {
            logger.error("Lỗi khi cập nhật avatar cho userId: {}", userId, e);
            return false;
        }
    }
}
