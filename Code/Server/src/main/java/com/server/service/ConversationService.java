package com.server.service;
import com.server.model.Conversation;
import com.server.repository.ConversationRepository;
import java.sql.SQLException;

public class ConversationService {

    private final ConversationRepository conversationRepo = new ConversationRepository();

    public long getOrCreatePrivateConversation(long user1Id, long user2Id) throws SQLException {
        Long existingId = conversationRepo.findPrivateConversation(user1Id, user2Id);

        if (existingId != null) {
            return existingId;
        }
        long newId = conversationRepo.createConversation(Conversation.ConversationType.PRIVATE, user1Id);
        conversationRepo.addMember(newId, user1Id);
        conversationRepo.addMember(newId, user2Id);

        return newId;
    }
}