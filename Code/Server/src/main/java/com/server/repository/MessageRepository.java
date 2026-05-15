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
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, message.getConversationId());
            pstmt.setLong(2, message.getSenderId());
            pstmt.setString(3, message.getType() != null ? message.getType().name() : Message.MessageType.TEXT.name());
            pstmt.setString(4, message.getContent());
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) return generatedKeys.getLong(1);
            }
        }
        return -1;
    }

    public List<Message> getByConversationId(long conversationId) {
        List<Message> messages = new ArrayList<>();
        String query = "SELECT id, conversation_id, sender_id, type, content, created_at " +
                       "FROM messages WHERE conversation_id = ? ORDER BY created_at ASC";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
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
