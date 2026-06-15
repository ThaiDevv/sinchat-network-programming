package com.server.service;

import com.server.config.Database;
import com.server.model.User;
import com.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class UserNameService {
    private static final Logger logger = LoggerFactory.getLogger(UserNameService.class);
    private final UserRepository userRepository = new UserRepository();

    public boolean updateUsername(long userId, String newUsername) {
        User currentUser = userRepository.findById(userId); // Sửa: thêm dấu ;
        if (currentUser == null) {
            logger.warn("Không tìm thấy user với ID: {}", userId);
            return false;
        }

        String oldUsername = currentUser.getUsername(); // Sửa: gọi đúng method

        if (newUsername.equals(oldUsername)) { // Sửa: bỏ try-catch thừa
            logger.warn("Username '{}' trùng với tên hiện tại của userId {}", newUsername, userId);
            return false;
        }

        // 4. Kiểm tra: tên mới đã bị user khác dùng chưa?
        User existingUser = userRepository.findByUsername(newUsername); // Sửa: thêm dấu ;
        if (existingUser != null) { // Sửa: if existingUser != null (không phải newUsername)
            logger.warn("Username '{}' đã được sử dụng bởi user khác", newUsername);
            return false;
        }

        return saveNewUsername(userId, newUsername, oldUsername); // Sửa: đúng tên method
    }

    private boolean saveNewUsername(long userId, String newUsername, String oldUsername) { // Sửa: String viết hoa
        String sql = "UPDATE users SET username = ? WHERE id = ?"; // Sửa: đúng tên bảng là "users"

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newUsername);
            stmt.setLong(2, userId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Đã đổi username từ '{}' sang '{}' cho userId {}", oldUsername, newUsername, userId);
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Lỗi khi cập nhật username cho userId {}", userId, e);
            return false;
        }
    }
}
