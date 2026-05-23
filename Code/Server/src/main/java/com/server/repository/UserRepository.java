package com.server.repository;

import com.server.config.Database;
import com.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    /** Tìm user theo username */
    public User findByUsername(String username) {
        String query = "SELECT id, username, password_hash, email, avatar_url, status_message, " +
                       "is_online, last_seen, created_at FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username: {}", username, e);
        }
        return null;
    }

    /** Tìm user theo ID */
    public User findById(long id) {
        String query = "SELECT id, username, password_hash, email, avatar_url, status_message, " +
                       "is_online, last_seen, created_at FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id: {}", id, e);
        }
        return null;
    }

    /** Lưu user mới vào DB */
    public boolean save(User user) throws SQLException {
        String query = "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getEmail());
            return pstmt.executeUpdate() > 0;
        }
    }

    /** Cập nhật mật khẩu mới */
    public boolean updatePassword(String username, String newPasswordHash) {
        String query = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating password for user: {}", username, e);
        }
        return false;
    }

    /** Cập nhật trạng thái online/offline */
    public void updateOnlineStatus(long userId, boolean isOnline) {
        String query = "UPDATE users SET is_online = ?, last_seen = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setBoolean(1, isOnline);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating online status for user: {}", userId, e);
        }
    }

    public void updateOnlineStatusWithoutLastSeen(long userId, boolean isOnline) {
        String query = "UPDATE users SET is_online = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setBoolean(1, isOnline);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating online status (without last_seen) for user: {}", userId, e);
        }
    }

    public java.util.List<Long> findAcceptedFriendIds(long userId) {
        java.util.List<Long> friendIds = new java.util.ArrayList<>();

        // friendships schema: (user1_id, user2_id, status)
        String query = "SELECT CASE WHEN user1_id = ? THEN user2_id ELSE user1_id END AS friend_id " +
                "FROM friendships WHERE (user1_id = ? OR user2_id = ?) AND status = 'ACCEPTED'";

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, userId);
            pstmt.setLong(3, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    friendIds.add(rs.getLong("friend_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding accepted friend ids for user: {}", userId, e);
        }
        return friendIds;
    }

    public Timestamp findLastSeen(long userId) {
        String query = "SELECT last_seen FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("last_seen");
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding last_seen for user: {}", userId, e);
        }
        return null;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("email"),
                rs.getString("avatar_url"),
                rs.getString("status_message"),
                rs.getBoolean("is_online"),
                rs.getTimestamp("last_seen"),
                rs.getTimestamp("created_at")
        );
    }
}
