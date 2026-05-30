package com.server.service;

import com.google.gson.JsonObject;
import com.server.model.MessageStatus;
import com.server.repository.ConversationRepository;
import com.server.repository.MessageStatusRepository;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MessageStatusService {
    private static final Logger logger = LoggerFactory.getLogger(MessageStatusService.class);
    private final MessageStatusRepository messageStatusRepository = new MessageStatusRepository();
    private final ConversationRepository conversationRepository = new ConversationRepository();

    /**
     * Khoi tao trang thai cho tat ca thanh vien khi tin nhan duoc gui.
     */
    public void initializeMessageStatus(long messageId, long senderId, long conversationId) {
        List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
        for (Long memberId : memberIds) {
            MessageStatus.Status status;
            if (memberId == senderId) {
                status = MessageStatus.Status.SEEN;
            } else if (TcpConnectionManager.getInstance().hasOnlineConnection(memberId)) {
                status = MessageStatus.Status.DELIVERED;
            } else {
                status = MessageStatus.Status.SENT;
            }
            messageStatusRepository.save(messageId, memberId, status);
        }
    }

    /**
     * Danh dau da doc cho toan bo tin nhan trong mot cuoc tro chuyen boi user cu the.
     */
    public void markConversationAsSeen(long conversationId, long userId) {
        int updatedCount = messageStatusRepository.updateAllUnreadInConversation(conversationId, userId, MessageStatus.Status.SEEN);
        if (updatedCount > 0) {
            logger.info("[STATUS SERVICE] User {} marked {} messages as SEEN in conversation {}", userId, updatedCount, conversationId);
            
            // Broadcast den cac thanh vien khac
            List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
            JsonObject broadcastMsg = new JsonObject();
            broadcastMsg.addProperty("action", "MESSAGE_STATUS_UPDATE");
            broadcastMsg.addProperty("conversationId", conversationId);
            broadcastMsg.addProperty("userId", userId);
            broadcastMsg.addProperty("status", MessageStatus.Status.SEEN.name());

            for (Long memberId : memberIds) {
                if (!memberId.equals(userId)) {
                    TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
                }
            }
        }
    }

    /**
     * Danh dau tat ca tin nhan chua nhan cua user khi user online/ket noi.
     */
    public void markAllConversationsAsDelivered(long userId) {
        List<Long> conversationIds = new java.util.ArrayList<>();
        for (com.server.model.Conversation c : conversationRepository.getConversationsByUserId(userId)) {
            conversationIds.add(c.getId());
        }

        for (Long conversationId : conversationIds) {
            int updatedCount = messageStatusRepository.updateAllUnreadInConversation(conversationId, userId, MessageStatus.Status.DELIVERED);
            if (updatedCount > 0) {
                logger.info("[STATUS SERVICE] User {} marked {} messages as DELIVERED in conversation {}", userId, updatedCount, conversationId);
                
                // Broadcast den cac thanh vien khac
                List<Long> memberIds = conversationRepository.getMemberIds(conversationId);
                JsonObject broadcastMsg = new JsonObject();
                broadcastMsg.addProperty("action", "MESSAGE_STATUS_UPDATE");
                broadcastMsg.addProperty("conversationId", conversationId);
                broadcastMsg.addProperty("userId", userId);
                broadcastMsg.addProperty("status", MessageStatus.Status.DELIVERED.name());

                for (Long memberId : memberIds) {
                    if (!memberId.equals(userId)) {
                        TcpConnectionManager.getInstance().broadcastToUser(memberId, broadcastMsg);
                    }
                }
            }
        }
    }
}
