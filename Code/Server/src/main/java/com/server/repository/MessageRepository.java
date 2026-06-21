package com.server.repository;

import com.server.config.Database;
import com.server.model.Message;
import com.server.model.MessageSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageRepository {
    private static final Logger logger = LoggerFactory.getLogger(MessageRepository.class);

    /**
     * Luu tin nhan moi vao bang messages.
     * Tra ve ID do database tu sinh.
     */
    public long save(Message message) throws SQLException {
        String query = "INSERT INTO messages (conversation_id, sender_id, type, content) VALUES (?, ?, ?, ?)";
        String updateConvQuery = "UPDATE conversations SET last_message_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement updateStmt = conn.prepareStatement(updateConvQuery)) {
                pstmt.setLong(1, message.getConversationId());
                pstmt.setLong(2, message.getSenderId());
                pstmt.setString(3, message.getType() != null ? message.getType().name() : Message.MessageType.TEXT.name());
                pstmt.setString(4, message.getContent());
                pstmt.executeUpdate();

                long generatedId = -1;
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        generatedId = generatedKeys.getLong(1);
                    }
                }

                updateStmt.setLong(1, message.getConversationId());
                updateStmt.executeUpdate();

                conn.commit();
                return generatedId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Message> getByConversationId(long conversationId) {
        return getByConversationId(conversationId, 0, 0);
    }

    public List<Message> getByConversationId(long conversationId, int limit, int offset) {
        List<Message> messages = new ArrayList<>();
        StringBuilder query = new StringBuilder(
                "SELECT m.id, m.conversation_id, m.sender_id, u.username AS sender_username, m.type, m.content, m.created_at " +
                "FROM messages m JOIN users u ON m.sender_id = u.id WHERE m.conversation_id = ? ORDER BY m.created_at DESC");
        if (limit > 0) query.append(" LIMIT ?");
        if (offset > 0) query.append(" OFFSET ?");
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query.toString())) {
            pstmt.setLong(1, conversationId);
            int idx = 2;
            if (limit > 0) pstmt.setInt(idx++, limit);
            if (offset > 0) pstmt.setInt(idx, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) messages.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching messages for conversation: {}", conversationId, e);
        }
        return messages;
    }

    public List<MessageSearchResult> searchByConversation(long conversationId, String keyword, int limit, int offset) {
        List<MessageSearchResult> messages = new ArrayList<>();
        // Escape SQL LIKE wildcards
        String escapedKeyword = keyword.replace("%", "\\%").replace("_", "\\_");
        String query = "SELECT m.id, m.conversation_id, m.sender_id, u.username AS sender_username, " +
                "m.type, m.content, m.created_at " +
                "FROM messages m " +
                "JOIN users u ON u.id = m.sender_id " +
                "WHERE m.conversation_id = ? AND LOWER(m.content) LIKE LOWER(?) " +
                "ORDER BY m.created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setString(2, "%" + escapedKeyword + "%");
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) messages.add(mapSearchRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error searching messages for conversation: {}", conversationId, e);
        }
        return messages;
    }

    private Message mapRow(ResultSet rs) throws SQLException {
        Message msg = new Message(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("sender_id"),
                Message.MessageType.valueOf(rs.getString("type")),
                rs.getString("content"),
                rs.getTimestamp("created_at")
        );
        try {
            msg.setSenderUsername(rs.getString("sender_username"));
        } catch (SQLException ignored) {}
        return msg;
    }

    private MessageSearchResult mapSearchRow(ResultSet rs) throws SQLException {
        return new MessageSearchResult(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("sender_id"),
                rs.getString("sender_username"),
                Message.MessageType.valueOf(rs.getString("type")),
                rs.getString("content"),
                rs.getTimestamp("created_at")
        );
    }
}
