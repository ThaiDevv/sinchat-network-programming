package com.server.service;
import com.server.model.Conversation;
import com.server.repository.ConversationRepository;
import com.google.gson.JsonArray;
import java.sql.SQLException;
import java.util.List;

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

    // ==================== GROUP MANAGEMENT ====================

    public String getMemberRole(long conversationId, long userId) {
        return conversationRepo.getMemberRole(conversationId, userId);
    }

    public long getConversationCreator(long conversationId) {
        Long creator = conversationRepo.getConversationCreator(conversationId);
        return creator != null ? creator : -1L;
    }

    public JsonArray getGroupMembers(long conversationId) {
        return conversationRepo.getMembersWithDetails(conversationId);
    }

    public void renameGroup(long conversationId, String newName) throws SQLException {
        conversationRepo.updateGroupName(conversationId, newName);
    }

    public void addGroupMember(long conversationId, long userId) throws SQLException {
        conversationRepo.addMemberWithRole(conversationId, userId, "MEMBER");
    }

    public void kickGroupMember(long conversationId, long userId) throws SQLException {
        conversationRepo.removeMember(conversationId, userId);
    }

    public void transferGroupAdmin(long conversationId, long currentAdminId, long newAdminId) throws SQLException {
        conversationRepo.transferAdmin(conversationId, currentAdminId, newAdminId);
    }

    public void disbandGroup(long conversationId) throws SQLException {
        conversationRepo.disbandGroup(conversationId);
    }

    public boolean isGroupMember(long conversationId, long userId) {
        return conversationRepo.isGroupMember(conversationId, userId);
    }

    public List<Long> getMemberIds(long conversationId) {
        return conversationRepo.getMemberIds(conversationId);
    }

    public String getConversationName(long conversationId) {
        return conversationRepo.getConversationName(conversationId);
    }
}
