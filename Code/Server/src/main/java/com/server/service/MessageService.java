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
        return sendMessage(conversationId, senderId, content, null, null);
    }

    public long sendMessage(long conversationId, long senderId, String content, Long replyToId) throws SQLException {
        return sendMessage(conversationId, senderId, content, replyToId, null);
    }

    public long sendMessage(long conversationId, long senderId, String content, Long replyToId, Long forwardFromId) throws SQLException {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(Message.MessageType.TEXT);
        message.setContent(content);
        message.setReplyToId(replyToId);
        message.setForwardFromId(forwardFromId);
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

    public Message getMessageById(long messageId) {
        return messageRepository.findById(messageId);
    }

    public boolean editMessage(long messageId, long senderId, String newContent) {
        Message msg = messageRepository.findById(messageId);
        if (msg == null) {
            return false;
        }
        if (msg.getSenderId() != senderId) {
            throw new SecurityException("Unauthorized: you cannot edit someone else's message");
        }
        return messageRepository.updateContent(messageId, newContent);
    }

    public boolean deleteMessage(long messageId, long senderId) {
        Message msg = messageRepository.findById(messageId);
        if (msg == null) {
            return false;
        }
        if (msg.getSenderId() != senderId) {
            throw new SecurityException("Unauthorized: you cannot delete someone else's message");
        }
        return messageRepository.updateContent(messageId, "Tin nhắn đã bị thu hồi");
    }
}


