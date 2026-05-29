package com.server.repository;

import com.server.config.Database;
import com.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageRepository {
    private static final Logger logger = LoggerFactory.getLogger(MessageRepository.class);

    /**
     * Lưu tin nhắn mới vào bảng messages.
     * Trả về ID được DB tự sinh (AUTO_INCREMENT).
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
                "SELECT id, conversation_id, sender_id, type, content, created_at " +
                "FROM messages WHERE conversation_id = ? ORDER BY created_at ASC");
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

    private Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("sender_id"),
                Message.MessageType.valueOf(rs.getString("type")),
                rs.getString("content"),
                rs.getTimestamp("created_at")
        );
    }
}
