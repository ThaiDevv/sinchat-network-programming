package com.server.repository;

import com.server.config.Database;
import com.server.model.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageStatusRepository {
    private static final Logger logger = LoggerFactory.getLogger(MessageStatusRepository.class);

    /**
     * Luu trang thai tin nhan ban dau.
     */
    public boolean save(long messageId, long userId, MessageStatus.Status status) {
        String query = "INSERT INTO message_status (message_id, user_id, status) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            pstmt.setLong(2, userId);
            pstmt.setString(3, status.name());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error saving message status for messageId: {}, userId: {}", messageId, userId, e);
        }
        return false;
    }

    /**
     * Cap nhat trang thai tin nhan.
     */
    public boolean update(long messageId, long userId, MessageStatus.Status status) {
        String query = "UPDATE message_status SET status = ? WHERE message_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, status.name());
            pstmt.setLong(2, messageId);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating message status for messageId: {}, userId: {}", messageId, userId, e);
        }
        return false;
    }

    /**
     * Cap nhat toan bo tin nhan chua doc cua nguoi dung trong cuoc tro chuyen.
     * Neu status muon cap nhat la SEEN, chi cap nhat nhung tin nhan dang o trang thai SENT hoac DELIVERED.
     * Neu status muon cap nhat la DELIVERED, chi cap nhat nhung tin nhan dang o trang thai SENT.
     */
    public int updateAllUnreadInConversation(long conversationId, long userId, MessageStatus.Status newStatus) {
        String query;
        if (newStatus == MessageStatus.Status.SEEN) {
            query = "UPDATE message_status ms " +
                    "JOIN messages m ON ms.message_id = m.id " +
                    "SET ms.status = 'SEEN' " +
                    "WHERE m.conversation_id = ? AND ms.user_id = ? AND ms.status IN ('SENT', 'DELIVERED')";
        } else if (newStatus == MessageStatus.Status.DELIVERED) {
            query = "UPDATE message_status ms " +
                    "JOIN messages m ON ms.message_id = m.id " +
                    "SET ms.status = 'DELIVERED' " +
                    "WHERE m.conversation_id = ? AND ms.user_id = ? AND ms.status = 'SENT'";
        } else {
            return 0;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating all unread in conversationId: {}, userId: {}, to status: {}",
                    conversationId, userId, newStatus, e);
        }
        return 0;
    }

    /**
     * Lay danh sach trang thai cua mot tin nhan cu the.
     */
    public List<MessageStatus> getByMessageId(long messageId) {
        List<MessageStatus> list = new ArrayList<>();
        String query = "SELECT message_id, user_id, status, updated_at FROM message_status WHERE message_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new MessageStatus(
                            rs.getLong("message_id"),
                            rs.getLong("user_id"),
                            MessageStatus.Status.valueOf(rs.getString("status")),
                            rs.getTimestamp("updated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting statuses for messageId: {}", messageId, e);
        }
        return list;
    }

    /**
     * Lay cac tin nhan va trang thai cua chung trong mot cuoc hoi thoai.
     */
    public List<MessageStatus> getStatusesForConversation(long conversationId) {
        List<MessageStatus> list = new ArrayList<>();
        String query = "SELECT ms.message_id, ms.user_id, ms.status, ms.updated_at " +
                "FROM message_status ms " +
                "JOIN messages m ON ms.message_id = m.id " +
                "WHERE m.conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new MessageStatus(
                            rs.getLong("message_id"),
                            rs.getLong("user_id"),
                            MessageStatus.Status.valueOf(rs.getString("status")),
                            rs.getTimestamp("updated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting statuses for conversationId: {}", conversationId, e);
        }
        return list;
    }
}
