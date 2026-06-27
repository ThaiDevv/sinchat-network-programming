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
        String query = "INSERT INTO messages (conversation_id, sender_id, type, content, reply_to_message_id, forward_from_id) VALUES (?, ?, ?, ?, ?, ?)";
        String updateConvQuery = "UPDATE conversations SET last_message_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement updateStmt = conn.prepareStatement(updateConvQuery)) {
                pstmt.setLong(1, message.getConversationId());
                pstmt.setLong(2, message.getSenderId());
                pstmt.setString(3, message.getType() != null ? message.getType().name() : Message.MessageType.TEXT.name());
                pstmt.setString(4, message.getContent());
                if (message.getReplyToId() != null) {
                    pstmt.setLong(5, message.getReplyToId());
                } else {
                    pstmt.setNull(5, java.sql.Types.BIGINT);
                }
                if (message.getForwardFromId() != null) {
                    pstmt.setLong(6, message.getForwardFromId());
                } else {
                    pstmt.setNull(6, java.sql.Types.BIGINT);
                }
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
                "SELECT m.id, m.conversation_id, m.sender_id, u.username AS sender_username, m.type, m.content, m.created_at, " +
                "m.reply_to_message_id, pu.username AS reply_to_username, pm.content AS reply_to_content, " +
                "m.forward_from_id, fu.username AS forward_from_username, fm.content AS forward_from_content " +
                "FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "LEFT JOIN messages pm ON m.reply_to_message_id = pm.id " +
                "LEFT JOIN users pu ON pm.sender_id = pu.id " +
                "LEFT JOIN messages fm ON m.forward_from_id = fm.id " +
                "LEFT JOIN users fu ON fm.sender_id = fu.id " +
                "WHERE m.conversation_id = ? ORDER BY m.created_at DESC");
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

    public Message findById(long messageId) {
        String query = "SELECT m.id, m.conversation_id, m.sender_id, u.username AS sender_username, m.type, m.content, m.created_at, " +
                "m.reply_to_message_id, pu.username AS reply_to_username, pm.content AS reply_to_content, " +
                "m.forward_from_id, fu.username AS forward_from_username, fm.content AS forward_from_content " +
                "FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "LEFT JOIN messages pm ON m.reply_to_message_id = pm.id " +
                "LEFT JOIN users pu ON pm.sender_id = pu.id " +
                "LEFT JOIN messages fm ON m.forward_from_id = fm.id " +
                "LEFT JOIN users fu ON fm.sender_id = fu.id " +
                "WHERE m.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, messageId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching message by ID: {}", messageId, e);
        }
        return null;
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
        try {
            long replyToIdVal = rs.getLong("reply_to_message_id");
            if (!rs.wasNull()) {
                msg.setReplyToId(replyToIdVal);
                msg.setReplyToUsername(rs.getString("reply_to_username"));
                msg.setReplyToContent(rs.getString("reply_to_content"));
            }
        } catch (SQLException ignored) {}
        try {
            long forwardFromIdVal = rs.getLong("forward_from_id");
            if (!rs.wasNull()) {
                msg.setForwardFromId(forwardFromIdVal);
                msg.setForwardFromUsername(rs.getString("forward_from_username"));
                msg.setForwardFromContent(rs.getString("forward_from_content"));
            }
        } catch (SQLException ignored) {}
        return msg;
    }

    private MessageSearchResult mapSearchRow(ResultSet rs) throws SQLException {
        return new MessageSearchResult(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("sender_id"),
                rs.getString("sender_username"),
                rs.getString("type") != null ? Message.MessageType.valueOf(rs.getString("type")) : Message.MessageType.TEXT,
                rs.getString("content"),
                rs.getTimestamp("created_at")
        );
    }

    public boolean updateContent(long messageId, String newContent) {
        String query = "UPDATE messages SET content = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, newContent);
            pstmt.setLong(2, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating message content for ID: {}", messageId, e);
        }
        return false;
    }
}

