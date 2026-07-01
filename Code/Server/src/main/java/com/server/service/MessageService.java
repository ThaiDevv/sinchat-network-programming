package com.server.service;

import com.server.model.Message;
import com.server.model.MessageStatus;

import com.server.model.MessageSearchResult;
import com.server.repository.ConversationRepository;
import com.server.repository.MessageRepository;
import com.server.repository.MessageStatusRepository;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.util.List;


public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
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
        Message.MessageType type = Message.MessageType.TEXT;
        if (forwardFromId != null) {
            Message fwd = messageRepository.findById(forwardFromId);
            if (fwd != null) {
                type = fwd.getType();
            }
        }
        return sendMessage(conversationId, senderId, content, type, replyToId, forwardFromId);
    }

    public long sendMessage(long conversationId, long senderId, String content, Message.MessageType type, Long replyToId, Long forwardFromId) throws SQLException {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setType(type != null ? type : Message.MessageType.TEXT);
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

    /**
     * Edit a message by creating a NEW message with the updated content,
     * then linking the old message to it via edited_to_id.
     * The original content is preserved; the new message is hidden from the
     * conversation view (excluded by NOT IN subquery on edited_to_id).
     *
     * @return the ID of the newly created edit message, or -1 on failure
     */
    public long editMessage(long messageId, long senderId, String newContent) {
        Message oldMsg = messageRepository.findById(messageId);
        if (oldMsg == null) {
            return -1;
        }
        if (oldMsg.getSenderId() != senderId) {
            throw new SecurityException("Unauthorized: you cannot edit someone else's message");
        }
        if (oldMsg.isDeleted()) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }

        try {
            // 1. Create a new message with the edited content (same conversation, same sender)
            Message newMsg = new Message();
            newMsg.setConversationId(oldMsg.getConversationId());
            newMsg.setSenderId(senderId);
            newMsg.setType(oldMsg.getType());
            newMsg.setContent(newContent);
            long newMsgId = messageRepository.save(newMsg);

            // 2. Initialize message status for recipients of the new message
            List<Long> memberIds = conversationRepository.getMemberIds(oldMsg.getConversationId());
            for (Long memberId : memberIds) {
                if (memberId != senderId) {
                    boolean isOnline = TcpConnectionManager.getInstance().hasOnlineConnection(memberId);
                    MessageStatus.Status initialStatus = isOnline ? MessageStatus.Status.DELIVERED : MessageStatus.Status.SENT;
                    messageStatusRepository.create(newMsgId, memberId, initialStatus);
                }
            }

            // 3. Link old message → new message (chain)
            messageRepository.markAsEdited(messageId, newMsgId);

            return newMsgId;
        } catch (SQLException e) {
            logger.error("Error creating edit message for original ID: {}", messageId, e);
            return -1;
        }
    }

    public boolean deleteMessage(long messageId, long senderId) {
        Message msg = messageRepository.findById(messageId);
        if (msg == null) {
            return false;
        }
        if (msg.getSenderId() != senderId) {
            throw new SecurityException("Unauthorized: you cannot delete someone else's message");
        }
        return messageRepository.softDelete(messageId);
    }
}


