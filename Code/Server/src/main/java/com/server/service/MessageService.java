package com.server.service;

import com.server.model.Message;
import com.server.repository.MessageRepository;
import com.server.repository.MessageStatusRepository;
import java.sql.SQLException;
import java.util.List;

public class MessageService {
    private final MessageRepository messageRepository = new MessageRepository();
    private final MessageStatusRepository messageStatusRepository = new MessageStatusRepository();


    public long sendMessage(long conversationId, long senderId, String content) throws SQLException {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(Message.MessageType.TEXT);
        message.setContent(content);
        return messageRepository.save(message);
    }


    public List<Message> getMessages(long conversationId) {
        List<Message> list = messageRepository.getByConversationId(conversationId);
        for (Message m : list) {
            m.setStatuses(messageStatusRepository.getByMessageId(m.getId()));
        }
        return list;
    }

    public List<Message> getMessages(long conversationId, int limit, int offset) {
        List<Message> list = messageRepository.getByConversationId(conversationId, limit, offset);
        for (Message m : list) {
            m.setStatuses(messageStatusRepository.getByMessageId(m.getId()));
        }
        return list;
    }
}
