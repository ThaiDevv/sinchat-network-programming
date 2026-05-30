package com.server.service;

import com.server.config.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.file.*;
import java.util.Base64;

public class AvatarService {
    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);
    private static final String UPLOAD_DIR = "uploads/avatars/";

    /** Maximum avatar file size: 5 MB */
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    public boolean changeAvatar(long userId, String avatarUrl) {
        try {
            // Kiểm tra format: data:image/png;base64,...
            if (avatarUrl == null || !avatarUrl.startsWith("data:image/")) {
                logger.warn("Invalid avatar data URI format for userId: {}", userId);
                return false;
            }

            // Giải mã Base64
            String[] parts = avatarUrl.split(",");
            if (parts.length < 2 || parts[1].isEmpty()) {
                logger.warn("Missing base64 data in avatar for userId: {}", userId);
                return false;
            }

            // Kiểm tra kích thước file (base64 ~ 4/3 kích thước thật)
            String base64Data = parts[1];
            long estimatedSize = (long) (base64Data.length() * 0.75);
            if (estimatedSize > MAX_FILE_SIZE_BYTES) {
                logger.warn("Avatar file too large: ~{} bytes for userId: {}", estimatedSize, userId);
                return false;
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // Kiểm tra header PNG (89 50 4E 47)
            if (imageBytes.length < 8 ||
                imageBytes[0] != (byte) 0x89 ||
                imageBytes[1] != (byte) 'P' ||
                imageBytes[2] != (byte) 'N' ||
                imageBytes[3] != (byte) 'G') {
                logger.warn("Avatar is not a valid PNG for userId: {}", userId);
                return false;
            }

            // Tạo tên file ngắn gọn
            String filename = "avatar_" + userId + "_" + System.currentTimeMillis() + ".png";
            Path filePath = Paths.get(UPLOAD_DIR, filename);
            
            // Đảm bảo thư mục tồn tại trước khi lưu
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            
            // Lưu file
            Files.write(filePath, imageBytes);
            
            // Cập nhật DB với đường dẫn ngắn
            String shortPath = UPLOAD_DIR + filename;
            return updateAvatarInDb(userId, shortPath);
            
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý avatar cho userId: {}", userId, e);
            return false;
        }
    }
    
    private boolean updateAvatarInDb(long userId, String avatarPath) {
        String updateAvatar = "UPDATE users SET avatar_url = ? WHERE id = ?";

        try (Connection conn = Database.getConnection();
                PreparedStatement stmt = conn.prepareStatement(updateAvatar)) {

            stmt.setString(1, avatarPath);
            stmt.setLong(2, userId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0; // false nếu user không tồn tại

        } catch (Exception e) {
            logger.error("Lỗi khi cập nhật avatar cho userId: {}", userId, e);
            return false;
        }
    }
}
