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
     * Prevents race condition where two concurrent calls could create duplicate
     * conversations.
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
                try (PreparedStatement createStmt = conn.prepareStatement(createQuery,
                        Statement.RETURN_GENERATED_KEYS)) {
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
        // For PRIVATE: show peer name & presence; for GROUP: show group name with no peer presence
        String query = "SELECT c.id, c.type, c.name AS group_name, " +
                "CASE WHEN c.type = 'PRIVATE' THEN u.username ELSE c.name END AS display_name, " +
                "u.id AS peer_id, " +
                "CASE WHEN c.type = 'PRIVATE' THEN COALESCE(u.is_online, 0) ELSE 0 END AS is_online, " +
                "u.last_seen AS last_seen, " +
                "(SELECT COALESCE(NULLIF(m.content, ''), fm.content, m.content) FROM messages m " +
                "LEFT JOIN messages fm ON m.forward_from_id = fm.id " +
                "WHERE m.conversation_id = c.id AND m.is_deleted = 0 " +
                "AND m.id NOT IN (SELECT edited_to_id FROM messages WHERE edited_to_id IS NOT NULL) " +
                "ORDER BY m.created_at DESC LIMIT 1) as last_message, " +
                "(SELECT m.type FROM messages m " +
                "WHERE m.conversation_id = c.id AND m.is_deleted = 0 " +
                "AND m.id NOT IN (SELECT edited_to_id FROM messages WHERE edited_to_id IS NOT NULL) " +
                "ORDER BY m.created_at DESC LIMIT 1) as last_message_type, " +
                "(SELECT m.sender_id FROM messages m " +
                "WHERE m.conversation_id = c.id AND m.is_deleted = 0 " +
                "AND m.id NOT IN (SELECT edited_to_id FROM messages WHERE edited_to_id IS NOT NULL) " +
                "ORDER BY m.created_at DESC LIMIT 1) as last_message_sender_id, " +
                "(SELECT u2.username FROM messages m JOIN users u2 ON m.sender_id = u2.id " +
                "WHERE m.conversation_id = c.id AND m.is_deleted = 0 " +
                "AND m.id NOT IN (SELECT edited_to_id FROM messages WHERE edited_to_id IS NOT NULL) " +
                "ORDER BY m.created_at DESC LIMIT 1) as last_message_sender_name, " +
                "c.last_message_at " +
                "FROM conversations c " +
                "JOIN conversation_members cm ON c.id = cm.conversation_id " +
                "LEFT JOIN conversation_members cm2 ON c.type = 'PRIVATE' AND c.id = cm2.conversation_id AND cm2.user_id != cm.user_id "
                +
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
                    String type = rs.getString("type");
                    obj.addProperty("type", type);

                    String displayName = rs.getString("display_name");
                    obj.addProperty("displayName", displayName != null ? displayName : "Unknown");

                    if ("GROUP".equals(type)) {
                        String groupName = rs.getString("group_name");
                        obj.addProperty("groupName", groupName != null ? groupName : "");
                    } else {
                        // PRIVATE: include peer info
                        long peerId = rs.getLong("peer_id");
                        if (!rs.wasNull()) {
                            obj.addProperty("peerId", peerId);
                        }
                        obj.addProperty("isOnline", rs.getBoolean("is_online"));

                        Timestamp lastSeen = rs.getTimestamp("last_seen");
                        if (lastSeen != null) {
                            obj.addProperty("lastSeen", TS_FMT.format(lastSeen.toLocalDateTime()));
                        }
                    }

                    String lastMessage = rs.getString("last_message");
                    obj.addProperty("lastMessage", lastMessage != null ? lastMessage : "");

                    String lastMessageType = rs.getString("last_message_type");
                    obj.addProperty("lastMessageType", lastMessageType != null ? lastMessageType : "TEXT");

                    long senderId = rs.getLong("last_message_sender_id");
                    if (!rs.wasNull()) {
                        obj.addProperty("lastMessageSenderId", senderId);
                    }

                    String senderName = rs.getString("last_message_sender_name");
                    obj.addProperty("lastMessageSenderName", senderName != null ? senderName : "");

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
     * Create a new GROUP conversation with a given name and member list.
     * Creator is assigned ADMIN role, others are MEMBER.
     */
    public long createGroupConversation(long creatorId, String groupName, List<Long> memberIds) throws SQLException {
        String createQuery = "INSERT INTO conversations (type, name, created_by) VALUES ('GROUP', ?, ?)";
        String addMemberQuery = "INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long newId;
                try (PreparedStatement createStmt = conn.prepareStatement(createQuery, Statement.RETURN_GENERATED_KEYS)) {
                    createStmt.setString(1, groupName);
                    createStmt.setLong(2, creatorId);
                    createStmt.executeUpdate();
                    try (ResultSet rs = createStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            newId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to create group conversation — no generated key");
                        }
                    }
                }

                try (PreparedStatement addStmt = conn.prepareStatement(addMemberQuery)) {
                    for (Long memberId : memberIds) {
                        addStmt.setLong(1, newId);
                        addStmt.setLong(2, memberId);
                        addStmt.setString(3, memberId == creatorId ? "ADMIN" : "MEMBER");
                        addStmt.executeUpdate();
                    }
                }

                conn.commit();
                logger.info("Created group conversation id={} name='{}' creatorId={} members={}",
                        newId, groupName, creatorId, memberIds);
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public String getConversationType(long conversationId) {
        String query = "SELECT type FROM conversations WHERE id = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("type");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching conversation type for id: {}", conversationId, e);
        }
        return null;
    }

    public void removeMember(long conversationId, long userId) throws SQLException {
        String query = "DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

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

    // ==================== GROUP MANAGEMENT ====================

    /**
     * Get the role of a user in a conversation ('ADMIN' or 'MEMBER').
     * Returns null if not a member.
     */
    public String getMemberRole(long conversationId, long userId) {
        String query = "SELECT role FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching member role for conversationId={} userId={}", conversationId, userId, e);
        }
        return null;
    }

    /**
     * Get detailed member list for a group conversation.
     * Returns a JsonArray with {userId, username, role, isOnline} for each member.
     */
    public JsonArray getMembersWithDetails(long conversationId) {
        JsonArray members = new JsonArray();
        String query = "SELECT cm.user_id, u.username, cm.role, u.is_online " +
                "FROM conversation_members cm " +
                "JOIN users u ON cm.user_id = u.id " +
                "WHERE cm.conversation_id = ? " +
                "ORDER BY FIELD(cm.role, 'ADMIN', 'MEMBER'), u.username";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject member = new JsonObject();
                    member.addProperty("userId", rs.getLong("user_id"));
                    member.addProperty("username", rs.getString("username"));
                    member.addProperty("role", rs.getString("role"));
                    member.addProperty("isOnline", rs.getBoolean("is_online"));
                    members.add(member);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching members for conversationId={}", conversationId, e);
        }
        return members;
    }

    /**
     * Update the name of a group conversation.
     */
    public void updateGroupName(long conversationId, String newName) throws SQLException {
        String query = "UPDATE conversations SET name = ? WHERE id = ? AND type = 'GROUP'";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, newName);
            pstmt.setLong(2, conversationId);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Group conversation not found or not a group: " + conversationId);
            }
        }
    }

    /**
     * Add a member to a conversation with a specified role.
     */
    public void addMemberWithRole(long conversationId, long userId, String role) throws SQLException {
        String query = "INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
        }
    }

    /**
     * Check if a user is a member of a conversation.
     */
    public boolean isGroupMember(long conversationId, long userId) {
        String query = "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking membership for conversationId={} userId={}", conversationId, userId, e);
        }
        return false;
    }

    /**
     * Transfer ADMIN role from current admin to a new admin.
     * Done atomically in a single transaction.
     */
    public void transferAdmin(long conversationId, long currentAdminId, long newAdminId) throws SQLException {
        String updateOld = "UPDATE conversation_members SET role = 'MEMBER' WHERE conversation_id = ? AND user_id = ?";
        String updateNew = "UPDATE conversation_members SET role = 'ADMIN' WHERE conversation_id = ? AND user_id = ?";
        String updateCreatedBy = "UPDATE conversations SET created_by = ? WHERE id = ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(updateOld)) {
                    stmt.setLong(1, conversationId);
                    stmt.setLong(2, currentAdminId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(updateNew)) {
                    stmt.setLong(1, conversationId);
                    stmt.setLong(2, newAdminId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(updateCreatedBy)) {
                    stmt.setLong(1, newAdminId);
                    stmt.setLong(2, conversationId);
                    stmt.executeUpdate();
                }
                conn.commit();
                logger.info("Transferred admin for conversation={} from userId={} to userId={}",
                        conversationId, currentAdminId, newAdminId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Disband (delete) a group conversation — removes all members and the conversation itself.
     * Done atomically in a single transaction.
     */
    public void disbandGroup(long conversationId) throws SQLException {
        String deleteMembers = "DELETE FROM conversation_members WHERE conversation_id = ?";
        String deleteMessageStatus = "DELETE FROM message_status WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = ?)";
        String deleteMessages = "DELETE FROM messages WHERE conversation_id = ?";
        String deleteConversation = "DELETE FROM conversations WHERE id = ? AND type = 'GROUP'";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(deleteMembers)) {
                    stmt.setLong(1, conversationId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(deleteMessageStatus)) {
                    stmt.setLong(1, conversationId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(deleteMessages)) {
                    stmt.setLong(1, conversationId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(deleteConversation)) {
                    stmt.setLong(1, conversationId);
                    int rows = stmt.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Group conversation not found: " + conversationId);
                    }
                }
                conn.commit();
                logger.info("Disbanded group conversation id={}", conversationId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Get the creator (created_by) of a conversation.
     */
    public Long getConversationCreator(long conversationId) {
        String query = "SELECT created_by FROM conversations WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long createdBy = rs.getLong("created_by");
                    return rs.wasNull() ? null : createdBy;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching creator for conversationId={}", conversationId, e);
        }
        return null;
    }

    /**
     * Get the name of a conversation.
     */
    public String getConversationName(long conversationId) {
        String query = "SELECT name FROM conversations WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching name for conversationId={}", conversationId, e);
        }
        return null;
    }
}

