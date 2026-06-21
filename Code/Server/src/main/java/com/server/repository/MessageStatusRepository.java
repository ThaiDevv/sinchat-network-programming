package com.server.repository;

import com.server.config.Database;
import com.server.model.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MessageStatusRepository {
    private static final Logger logger = LoggerFactory.getLogger(MessageStatusRepository.class);

    /**
     * Inserts a status record for a message recipient.
     */
    public void create(long messageId, long userId, MessageStatus.Status status) {
        String query = "INSERT INTO message_status (message_id, user_id, status) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            pstmt.setLong(2, userId);
            pstmt.setString(3, status.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error creating message status for messageId={}, userId={}", messageId, userId, e);
        }
    }

    /**
     * Updates status for a message recipient.
     */
    public void update(long messageId, long userId, MessageStatus.Status status) {
        String query = "UPDATE message_status SET status = ? WHERE message_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, status.name());
            pstmt.setLong(2, messageId);
            pstmt.setLong(3, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating message status to {} for messageId={}, userId={}", status, messageId, userId, e);
        }
    }

    /**
     * Updates all message statuses in a conversation to SEEN for a specific recipient.
     */
    public void markAllAsSeen(long conversationId, long userId) {
        String query = "UPDATE message_status ms " +
                       "JOIN messages m ON ms.message_id = m.id " +
                       "SET ms.status = 'SEEN' " +
                       "WHERE m.conversation_id = ? AND ms.user_id = ? AND ms.status != 'SEEN'";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error marking all messages as seen for conversationId={}, userId={}", conversationId, userId, e);
        }
    }

    /**
     * Get the statuses of messages in a conversation.
     * Maps messageId -> Status.
     */
    public Map<Long, MessageStatus.Status> getStatusesForConversation(long conversationId) {
        Map<Long, MessageStatus.Status> results = new HashMap<>();
        // In a typical conversation, the sender wants to see the collective status of their sent messages.
        // We select the status of each message. If multiple recipients exist (group chat),
        // we can take the "highest" status or the minimum. For now, let's select the status(es).
        String query = "SELECT ms.message_id, ms.status FROM message_status ms " +
                       "JOIN messages m ON ms.message_id = m.id " +
                       "WHERE m.conversation_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long msgId = rs.getLong("message_id");
                    String statusStr = rs.getString("status");
                    MessageStatus.Status status = MessageStatus.Status.valueOf(statusStr);
                    
                    // If a message has multiple status records, resolve to the most advanced status
                    // (SENT < DELIVERED < SEEN)
                    if (results.containsKey(msgId)) {
                        MessageStatus.Status existing = results.get(msgId);
                        if (status.ordinal() > existing.ordinal()) {
                            results.put(msgId, status);
                        }
                    } else {
                        results.put(msgId, status);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving message statuses for conversationId={}", conversationId, e);
        }
        return results;
    }

    /**
     * Returns the status of a specific message.
     */
    public MessageStatus.Status getStatus(long messageId, long userId) {
        String query = "SELECT status FROM message_status WHERE message_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return MessageStatus.Status.valueOf(rs.getString("status"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting status for messageId={}, userId={}", messageId, userId, e);
        }
        return null;
    }

    /**
     * Returns the collective status of a message (minimum status across all recipients).
     */
    public MessageStatus.Status getCollectiveStatus(long messageId) {
        String query = "SELECT status FROM message_status WHERE message_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean hasSent = false;
                boolean hasDelivered = false;
                int count = 0;
                while (rs.next()) {
                    count++;
                    String statusStr = rs.getString("status");
                    if ("SENT".equals(statusStr)) hasSent = true;
                    else if ("DELIVERED".equals(statusStr)) hasDelivered = true;
                }
                if (count == 0) return MessageStatus.Status.SENT;
                // Return the lowest/worst status across all recipients
                if (hasSent) return MessageStatus.Status.SENT;
                if (hasDelivered) return MessageStatus.Status.DELIVERED;
                return MessageStatus.Status.SEEN;
            }
        } catch (SQLException e) {
            logger.error("Error getting collective status for messageId={}", messageId, e);
        }
        return MessageStatus.Status.SENT;
    }

    /**
     * Get the list of users who have marked a message as SEEN.
     * Maps messageId -> List of SeenUserInfo.
     */
    public Map<Long, java.util.List<com.server.model.Message.SeenUserInfo>> getSeenUsersForConversation(long conversationId) {
        Map<Long, java.util.List<com.server.model.Message.SeenUserInfo>> results = new HashMap<>();
        String query = "SELECT ms.message_id, ms.user_id, u.username FROM message_status ms " +
                       "JOIN messages m ON ms.message_id = m.id " +
                       "JOIN users u ON ms.user_id = u.id " +
                       "WHERE m.conversation_id = ? AND ms.status = 'SEEN'";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long msgId = rs.getLong("message_id");
                    long userId = rs.getLong("user_id");
                    String username = rs.getString("username");
                    results.computeIfAbsent(msgId, k -> new java.util.ArrayList<>())
                           .add(new com.server.model.Message.SeenUserInfo(userId, username));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving seen users for conversationId={}", conversationId, e);
        }
        return results;
    }
}

