package com.server.service;

import com.server.model.Message;
import com.server.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final MessageRepository messageRepository = new MessageRepository();
    private final ConversationService conversationService = new ConversationService();


    public long sendMessage(Long conversationId, long senderId, Long receiverId, String content) throws SQLException {
        if (conversationId == null || conversationId <= 0) {
            if (receiverId != null && receiverId > 0) {
                conversationId = conversationService.getOrCreatePrivateConversation(senderId, receiverId);
            } else {
                throw new SQLException("Either conversationId or receiverId must be provided");
            }
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(Message.MessageType.TEXT);
        message.setContent(content);
        
        return messageRepository.save(message);
    }

    public List<Message> getMessages(long conversationId) {
        return messageRepository.getByConversationId(conversationId);
    }
}
