package com.server.service;

import com.server.config.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class AvatarService {
    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);

    /** Maximum avatar file size to parse (before compression): 10 MB */
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int TARGET_SIZE = 512;

    public boolean changeAvatar(long userId, String avatarUrl) {
        try {
            // Kiểm tra format data:image/...;base64,...
            if (avatarUrl == null || !avatarUrl.startsWith("data:image/")) {
                logger.warn("Invalid avatar data URI format for userId: {}", userId);
                return false;
            }

            // Giải mã Base64.
            String[] parts = avatarUrl.split(",");
            if (parts.length < 2 || parts[1].isEmpty()) {
                logger.warn("Missing base64 data in avatar for userId: {}", userId);
                return false;
            }

            String base64Data = parts[1];
            long estimatedSize = (long) (base64Data.length() * 0.75);
            if (estimatedSize > MAX_FILE_SIZE_BYTES) {
                logger.warn("Avatar file too large: ~{} bytes for userId: {}", estimatedSize, userId);
                return false;
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);

            // Compress và resize ảnh về tối đa 512x512 PNG
            byte[] processedBytes = resizeAndCompressImage(imageBytes, TARGET_SIZE);
            if (processedBytes == null) {
                logger.warn("Failed to process image (resize/compress) for userId: {}", userId);
                return false;
            }

            // Lưu avatar dạng BLOB vào bảng user_avatars và cập nhật avatar_url trong bảng users
            return saveAvatarToDb(userId, processedBytes);
            
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý avatar cho userId: {}", userId, e);
            return false;
        }
    }

    /**
     * Resize ảnh về tối đa 512x512 và nén dưới dạng PNG.
     */
    private byte[] resizeAndCompressImage(byte[] imageBytes, int targetSize) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) {
                logger.error("ImageIO.read returned null. Invalid image bytes.");
                return null;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // Nếu ảnh nhỏ hơn hoặc bằng 512x512, không cần resize kích thước, nhưng vẫn ghi lại dạng PNG để chuẩn hoá/nén.
            if (originalWidth <= targetSize && originalHeight <= targetSize) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(originalImage, "png", baos);
                    return baos.toByteArray();
                }
            }

            // Tính toán kích thước mới giữ nguyên tỉ lệ aspect ratio
            double ratio = (double) originalWidth / originalHeight;
            int newWidth, newHeight;
            if (originalWidth > originalHeight) {
                newWidth = targetSize;
                newHeight = (int) (targetSize / ratio);
            } else {
                newHeight = targetSize;
                newWidth = (int) (targetSize * ratio);
            }

            // Đảm bảo kích thước tối thiểu là 1x1
            newWidth = Math.max(1, newWidth);
            newHeight = Math.max(1, newHeight);

            // Resize ảnh chất lượng cao
            Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            
            Graphics2D g2d = resizedImage.createGraphics();
            // Thiết lập chế độ vẽ chất lượng cao
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            // Nén thành mảng byte định dạng PNG
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(resizedImage, "png", baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            logger.error("Lỗi trong quá trình resize và compress ảnh", e);
            return null;
        }
    }
    
    private boolean saveAvatarToDb(long userId, byte[] avatarBytes) {
        String insertOrUpdateSql = "INSERT INTO user_avatars (id, avatar) VALUES (?, ?) " +
                                   "ON DUPLICATE KEY UPDATE avatar = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmtAvatar = conn.prepareStatement(insertOrUpdateSql)) {

            // 1. Lưu avatar vào user_avatars
            stmtAvatar.setLong(1, userId);
            stmtAvatar.setBytes(2, avatarBytes);
            stmtAvatar.setBytes(3, avatarBytes);
            int rows = stmtAvatar.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            logger.error("Lỗi khi lưu avatar vào database cho userId: {}", userId, e);
            return false;
        }
    }

    /**
     * Lấy avatar dạng raw byte array từ database
     */
    public byte[] getAvatarBytes(long userId) {
        String query = "SELECT avatar FROM user_avatars WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("avatar");
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi khi lấy avatar BLOB từ DB cho userId: {}", userId, e);
        }
        return null;
    }
}
