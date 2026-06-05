package com.server.service;
import com.server.repository.ConversationRepository;
import java.sql.SQLException;

public class ConversationService {

    private final ConversationRepository conversationRepo = new ConversationRepository();

    public long getOrCreatePrivateConversation(long user1Id, long user2Id) throws SQLException {
        return conversationRepo.findOrCreatePrivateConversation(user1Id, user2Id);
    }

    public com.google.gson.JsonArray getConversationsWithDetails(long userId) {
        return conversationRepo.getConversationsWithDetails(userId);
    }
}