package com.server.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.config.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Repository for the `friendships` table.
 * Convention: user1_id < user2_id to avoid duplicates.
 * action_user_id stores who initiated the last action (sent request / blocked).
 */
public class FriendshipRepository {
    private static final Logger logger = LoggerFactory.getLogger(FriendshipRepository.class);

    // Helpers
    private long minId(long a, long b) {
        return Math.min(a, b);
    }

    private long maxId(long a, long b) {
        return Math.max(a, b);
    }

    // Write Operations
    /**
     * Gui loi moi ket ban
     * 
     * @return "sent", "already_friends", "pending_sent", "pending_received",
     *         "blocked"
     */
    public String sendFriendRequest(long senderId, long receiverId) {
        long u1 = minId(senderId, receiverId);
        long u2 = maxId(senderId, receiverId);

        try (Connection conn = Database.getConnection()) {
            // Kiem tra trang thai hien tai
            String checkSql = "SELECT status, action_user_id FROM friendships WHERE user1_id=? AND user2_id=?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        long actionUser = rs.getLong("action_user_id");
                        if ("ACCEPTED".equals(status))
                            return "already_friends";
                        if ("BLOCKED".equals(status))
                            return "blocked";
                        if ("PENDING".equals(status)) {
                            // Neu chinh sender la nguoi gui trc do → da gui
                            if (actionUser == senderId)
                                return "pending_sent";
                            // Nguoi kia da gui cho minh → accept tu dong? Khong, chi bao "pending_received"
                            return "pending_received";
                        }
                    }
                }
            }
            // Chua co row → INSERT
            String insertSql = "INSERT INTO friendships (user1_id, user2_id, status, action_user_id) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                ps.setString(3, "PENDING");
                ps.setLong(4, senderId);
                ps.executeUpdate();
            }
            return "sent";
        } catch (SQLException e) {
            logger.error("sendFriendRequest error senderId={} receiverId={}: {}", senderId, receiverId, e.getMessage(),
                    e);
            return "error";
        }
    }

    /**
     * Phan hoi loi moi ket ban.
     * 
     * @param newStatus "ACCEPTED" hoac "REJECTED" (xoa row) hoac "BLOCKED"
     * @return true neu thanh cong
     */
    public boolean respondToRequest(long responderId, long requesterId, String newStatus) {
        long u1 = minId(responderId, requesterId);
        long u2 = maxId(responderId, requesterId);
        try (Connection conn = Database.getConnection()) {
            if ("REJECTED".equals(newStatus)) {
                // Xoa row khi tu choi
                String sql = "DELETE FROM friendships WHERE user1_id=? AND user2_id=? AND status='PENDING' AND action_user_id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, u1);
                    ps.setLong(2, u2);
                    ps.setLong(3, requesterId);
                    return ps.executeUpdate() > 0;
                }
            } else {
                // Update trang thai
                String sql = "UPDATE friendships SET status=?, action_user_id=? WHERE user1_id=? AND user2_id=? AND status='PENDING' AND action_user_id=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, responderId);
                    ps.setLong(3, u1);
                    ps.setLong(4, u2);
                    ps.setLong(5, requesterId);
                    return ps.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("respondToRequest error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Huy loi moi da gui (cancel pending).
     */
    public boolean cancelFriendRequest(long senderId, long receiverId) {
        long u1 = minId(senderId, receiverId);
        long u2 = maxId(senderId, receiverId);
        try (Connection conn = Database.getConnection()) {
            String sql = "DELETE FROM friendships WHERE user1_id=? AND user2_id=? AND status='PENDING' AND action_user_id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                ps.setLong(3, senderId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.error("cancelFriendRequest error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Huy ket ban (xoa row ACCEPTED).
     */
    public boolean unfriend(long userId, long otherId) {
        long u1 = minId(userId, otherId);
        long u2 = maxId(userId, otherId);
        try (Connection conn = Database.getConnection()) {
            String sql = "DELETE FROM friendships WHERE user1_id=? AND user2_id=? AND status='ACCEPTED'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.error("unfriend error: {}", e.getMessage(), e);
            return false;
        }
    }

    // Read Operations
    /**
     * Lay trang thai quan he giua hai user.
     * 
     * @return "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "ACCEPTED" | "BLOCKED"
     */
    public String getFriendshipStatus(long viewerId, long otherId) {
        long u1 = minId(viewerId, otherId);
        long u2 = maxId(viewerId, otherId);
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT status, action_user_id FROM friendships WHERE user1_id=? AND user2_id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        long actionUser = rs.getLong("action_user_id");
                        if ("ACCEPTED".equals(status))
                            return "ACCEPTED";
                        if ("BLOCKED".equals(status))
                            return "BLOCKED";
                        if ("PENDING".equals(status)) {
                            return (actionUser == viewerId) ? "PENDING_SENT" : "PENDING_RECEIVED";
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("getFriendshipStatus error: {}", e.getMessage(), e);
        }
        return "NONE";
    }

    /**
     * Lay danh sach loi moi den (PENDING, nguoi khac gui cho userId).
     */
    public JsonArray getPendingRequests(long userId) {
        JsonArray arr = new JsonArray();
        String sql = "SELECT u.id, u.username, " +
                "(CASE WHEN ua.id IS NOT NULL THEN 'db' ELSE NULL END) AS avatar_url, " +
                "f.created_at " +
                "FROM friendships f " +
                "JOIN users u ON u.id = f.action_user_id " +
                "LEFT JOIN user_avatars ua ON ua.id = u.id " +
                "WHERE f.status='PENDING' AND f.action_user_id != ? " +
                "AND (f.user1_id=? OR f.user2_id=?) " +
                "ORDER BY f.created_at DESC";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("userId", rs.getLong("id"));
                    obj.addProperty("username", rs.getString("username"));
                    obj.addProperty("avatarUrl", rs.getString("avatar_url"));
                    arr.add(obj);
                }
            }
        } catch (SQLException e) {
            logger.error("getPendingRequests error userId={}: {}", userId, e.getMessage(), e);
        }
        return arr;
    }

    /**
     * Lay danh sach loi moi da gui (PENDING, userId la nguoi gui).
     */
    public JsonArray getSentRequests(long userId) {
        JsonArray arr = new JsonArray();
        String sql = "SELECT u.id, u.username, " +
                "(CASE WHEN ua.id IS NOT NULL THEN 'db' ELSE NULL END) AS avatar_url " +
                "FROM friendships f " +
                "JOIN users u ON u.id = CASE WHEN f.user1_id = ? THEN f.user2_id ELSE f.user1_id END " +
                "LEFT JOIN user_avatars ua ON ua.id = u.id " +
                "WHERE f.status='PENDING' AND f.action_user_id=? " +
                "AND (f.user1_id=? OR f.user2_id=?) " +
                "ORDER BY f.created_at DESC";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            ps.setLong(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("userId", rs.getLong("id"));
                    obj.addProperty("username", rs.getString("username"));
                    obj.addProperty("avatarUrl", rs.getString("avatar_url"));
                    arr.add(obj);
                }
            }
        } catch (SQLException e) {
            logger.error("getSentRequests error userId={}: {}", userId, e.getMessage(), e);
        }
        return arr;
    }

    /**
     * Lay danh sach ban be (ACCEPTED).
     */
    public JsonArray getFriendList(long userId) {
        JsonArray arr = new JsonArray();
        String sql = "SELECT u.id, u.username, u.is_online, u.last_seen, " +
                "(CASE WHEN ua.id IS NOT NULL THEN 'db' ELSE NULL END) AS avatar_url " +
                "FROM friendships f " +
                "JOIN users u ON u.id = CASE WHEN f.user1_id=? THEN f.user2_id ELSE f.user1_id END " +
                "LEFT JOIN user_avatars ua ON ua.id = u.id " +
                "WHERE f.status='ACCEPTED' AND (f.user1_id=? OR f.user2_id=?) " +
                "ORDER BY u.is_online DESC, u.username ASC";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("userId", rs.getLong("id"));
                    obj.addProperty("username", rs.getString("username"));
                    obj.addProperty("isOnline", rs.getBoolean("is_online"));
                    obj.addProperty("avatarUrl", rs.getString("avatar_url"));
                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    if (lastSeen != null) {
                        obj.addProperty("lastSeen", lastSeen.toString());
                    }
                    arr.add(obj);
                }
            }
        } catch (SQLException e) {
            logger.error("getFriendList error userId={}: {}", userId, e.getMessage(), e);
        }
        return arr;
    }

    /**
     * Dem so loi moi ket ban chua xu ly.
     */
    public int countPendingRequests(long userId) {
        String sql = "SELECT COUNT(*) FROM friendships WHERE status='PENDING' AND action_user_id != ? " +
                "AND (user1_id=? OR user2_id=?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("countPendingRequests error: {}", e.getMessage(), e);
        }
        return 0;
    }
}