package com.server.repository;

import com.server.config.Database;
import com.server.model.Conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversationRepository {
    private static final Logger logger = LoggerFactory.getLogger(ConversationRepository.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Long findPrivateConversation(long user1Id, long user2Id) throws SQLException {
        String query = "SELECT c.id FROM conversations c " +
                "JOIN conversation_members cm1 ON c.id = cm1.conversation_id " +
                "JOIN conversation_members cm2 ON c.id = cm2.conversation_id " +
                "WHERE c.type = 'PRIVATE' AND cm1.user_id = ? AND cm2.user_id = ?";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, user1Id);
            pstmt.setLong(2, user2Id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getLong("id");
            }
        }
        return null;
    }

    /**
     * Atomically find or create a private conversation within a single transaction.
     * Prevents race condition where two concurrent calls could create duplicate conversations.
     */
    public long findOrCreatePrivateConversation(long user1Id, long user2Id) throws SQLException {
        String findQuery = "SELECT c.id FROM conversations c " +
                "JOIN conversation_members cm1 ON c.id = cm1.conversation_id " +
                "JOIN conversation_members cm2 ON c.id = cm2.conversation_id " +
                "WHERE c.type = 'PRIVATE' AND cm1.user_id = ? AND cm2.user_id = ? FOR UPDATE";
        String createQuery = "INSERT INTO conversations (type, created_by) VALUES (?, ?)";
        String addMemberQuery = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Try to find existing conversation with row lock
                try (PreparedStatement findStmt = conn.prepareStatement(findQuery)) {
                    findStmt.setLong(1, user1Id);
                    findStmt.setLong(2, user2Id);
                    try (ResultSet rs = findStmt.executeQuery()) {
                        if (rs.next()) {
                            long existingId = rs.getLong("id");
                            conn.commit();
                            return existingId;
                        }
                    }
                }

                // Not found — create new conversation and add both members
                long newId;
                try (PreparedStatement createStmt = conn.prepareStatement(createQuery, Statement.RETURN_GENERATED_KEYS)) {
                    createStmt.setString(1, "PRIVATE");
                    createStmt.setLong(2, user1Id);
                    createStmt.executeUpdate();
                    try (ResultSet rs = createStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            newId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to create conversation — no generated key");
                        }
                    }
                }

                try (PreparedStatement addStmt = conn.prepareStatement(addMemberQuery)) {
                    addStmt.setLong(1, newId);
                    addStmt.setLong(2, user1Id);
                    addStmt.executeUpdate();

                    addStmt.setLong(1, newId);
                    addStmt.setLong(2, user2Id);
                    addStmt.executeUpdate();
                }

                conn.commit();
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public long createConversation(Conversation.ConversationType type, Long createdBy) throws SQLException {
        String query = "INSERT INTO conversations (type, created_by) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, type.name());
            if (createdBy != null)
                pstmt.setLong(2, createdBy);
            else
                pstmt.setNull(2, Types.BIGINT);
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next())
                    return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to create conversation");
    }

    public void addMember(long conversationId, long userId) throws SQLException {
        String query = "INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    public List<Conversation> getConversationsByUserId(long userId) {
        List<Conversation> list = new ArrayList<>();
        String query = "SELECT c.* FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "WHERE cm.user_id = ? ORDER BY c.last_message_at DESC";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Conversation(
                            rs.getLong("id"),
                            Conversation.ConversationType.valueOf(rs.getString("type")),
                            rs.getString("name"),
                            rs.getString("avatar_url"),
                            rs.getLong("created_by"),
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("last_message_at")));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching conversations for userId: {}", userId, e);
        }
        return list;
    }

    public JsonArray getConversationsWithDetails(long userId) {
        JsonArray array = new JsonArray();
        String query = "SELECT c.id, c.type, " +
                "CASE WHEN c.type = 'PRIVATE' THEN u.username ELSE c.name END AS display_name, " +
                "u.id AS peer_id, " +
                "CASE WHEN c.type = 'PRIVATE' THEN COALESCE(u.is_online, 0) ELSE 0 END AS is_online, " +
                "u.last_seen AS last_seen, " +
                "(SELECT content FROM messages m WHERE m.conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message, " +
                "(SELECT sender_id FROM messages m WHERE m.conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message_sender_id, " +
                "c.last_message_at " +
                "FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "LEFT JOIN conversation_members cm2 ON c.type = 'PRIVATE' AND c.id = cm2.conversation_id AND cm2.user_id != cm.user_id " +
                "LEFT JOIN users u ON cm2.user_id = u.id " +
                "WHERE cm.user_id = ? " +
                "ORDER BY c.last_message_at DESC";

        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("conversationId", rs.getLong("id"));
                    obj.addProperty("type", rs.getString("type"));

                    String displayName = rs.getString("display_name");
                    obj.addProperty("displayName", displayName != null ? displayName : "Unknown");

                    long peerId = rs.getLong("peer_id");
                    if (!rs.wasNull()) {
                        obj.addProperty("peerId", peerId);
                    }
                    obj.addProperty("isOnline", rs.getBoolean("is_online"));

                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    if (lastSeen != null) {
                        obj.addProperty("lastSeen", TS_FMT.format(lastSeen.toLocalDateTime()));
                    }

                    String lastMessage = rs.getString("last_message");
                    obj.addProperty("lastMessage", lastMessage != null ? lastMessage : "");

                    long senderId = rs.getLong("last_message_sender_id");
                    if (!rs.wasNull()) {
                        obj.addProperty("lastMessageSenderId", senderId);
                    }

                    Timestamp ts = rs.getTimestamp("last_message_at");
                    if (ts != null)
                        obj.addProperty("lastMessageAt", ts.toString());

                    array.add(obj);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching conversations with details for userId: {}", userId, e);
        }
        return array;
    }

    public List<Long> getMemberIds(long conversationId) {
        List<Long> memberIds = new ArrayList<>();
        String query = "SELECT user_id FROM conversation_members WHERE conversation_id = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(rs.getLong("user_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching member IDs for conversationId: {}", conversationId, e);
        }
        return memberIds;
    }

    /**
     * Find all distinct user IDs that share at least one conversation with the given user.
     * Used to broadcast presence changes to conversation peers (not just friends).
     */
    public List<Long> findConversationPeers(long userId) {
        List<Long> peerIds = new ArrayList<>();
        String query = "SELECT DISTINCT cm2.user_id " +
                "FROM conversation_members cm1 " +
                "JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id " +
                "WHERE cm1.user_id = ? AND cm2.user_id != ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    peerIds.add(rs.getLong("user_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding conversation peers for userId: {}", userId, e);
        }
        return peerIds;
    }
}
