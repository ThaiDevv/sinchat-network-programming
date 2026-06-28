package com.server.service;
import java.sql.SQLException;
import java.util.List;

import com.server.repository.ConversationRepository;

public class ConversationService {

    private final ConversationRepository conversationRepo = new ConversationRepository();

    public long getOrCreatePrivateConversation(long user1Id, long user2Id) throws SQLException {
        return conversationRepo.findOrCreatePrivateConversation(user1Id, user2Id);
    }

    public com.google.gson.JsonArray getConversationsWithDetails(long userId) {
        return conversationRepo.getConversationsWithDetails(userId);
    }

    /**
     * Create a new GROUP conversation with the given name and member list.
     *
     * @param creatorId the user who creates the group
     * @param groupName display name for the group
     * @param memberIds list of user IDs to add (must include creatorId)
     * @return the new conversation ID
     */
    public long createGroupConversation(long creatorId, String groupName, List<Long> memberIds) throws SQLException {
        return conversationRepo.createGroupConversation(creatorId, groupName, memberIds);
    }

    public void leaveGroupConversation(long conversationId, long userId) throws SQLException {
        conversationRepo.removeMember(conversationId, userId);
    }

    public String getConversationType(long conversationId) {
        return conversationRepo.getConversationType(conversationId);
    }

    // ==================== PIN / ROLE METHODS ====================

    public String getUserRole(long convId, long userId) {
        return conversationRepo.getUserRoleInConversation(convId, userId);
    }

    public boolean isAdminOnlyPinEnabled(long convId) {
        return conversationRepo.isAdminOnlyPinEnabled(convId);
    }

    public int getPinLimit(long convId) {
        return conversationRepo.getPinLimit(convId);
    }

    public void setAdminOnlyPin(long convId, boolean flag) throws SQLException {
        conversationRepo.setAdminOnlyPin(convId, flag);
    }

    public void addConversationRole(long convId, long userId, String role) throws SQLException {
        conversationRepo.addConversationRole(convId, userId, role);
    }
}