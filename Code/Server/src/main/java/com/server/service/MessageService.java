package com.server.service;

import com.server.model.Message;
import com.server.repository.MessageRepository;
import java.sql.SQLException;
import java.util.List;

public class MessageService {
    private final MessageRepository messageRepository = new MessageRepository();


    public long sendMessage(long conversationId, long senderId, String content) throws SQLException {
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
