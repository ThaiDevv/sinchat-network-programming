package com.server.service;

import com.server.model.Message;
import com.server.model.MessageStatus;

import com.server.model.MessageSearchResult;
import com.server.repository.ConversationRepository;
import com.server.repository.MessageRepository;
import com.server.repository.MessageStatusRepository;
import com.server.tcp.TcpConnectionManager;
import java.sql.SQLException;
import java.util.List;


public class MessageService {
    private final MessageRepository messageRepository = new MessageRepository();
    private final ConversationRepository conversationRepository = new ConversationRepository();
    private final MessageStatusRepository messageStatusRepository = new MessageStatusRepository();

    public long sendMessage(long conversationId, long senderId, String content) throws SQLException {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(Message.MessageType.TEXT);
        message.setContent(content);
        long msgId = messageRepository.save(message);

        // Initialize message status for all recipients
        List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
        for (Long memberId : memberIds) {
            if (memberId != senderId) {
                // If online, status is DELIVERED, otherwise SENT
                boolean isOnline = TcpConnectionManager.getInstance().hasOnlineConnection(memberId);
                MessageStatus.Status initialStatus = isOnline ? MessageStatus.Status.DELIVERED : MessageStatus.Status.SENT;
                messageStatusRepository.create(msgId, memberId, initialStatus);
            }
        }

        return msgId;
    }

    public List<Message> getMessages(long conversationId) {
        return messageRepository.getByConversationId(conversationId);
    }

    public List<Message> getMessages(long conversationId, int limit, int offset) {
        return messageRepository.getByConversationId(conversationId, limit, offset);
    }

    public List<MessageSearchResult> searchMessages(long conversationId, String keyword, int limit, int offset) {
        return messageRepository.searchByConversation(conversationId, keyword, limit, offset);
    }
}

