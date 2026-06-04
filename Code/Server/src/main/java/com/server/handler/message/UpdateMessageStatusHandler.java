package com.server.handler.message;

import com.google.gson.JsonObject;
import com.server.model.MessageStatus;
import com.server.repository.ConversationRepository;
import com.server.repository.MessageStatusRepository;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TCP handler for updating read/delivery status of messages.
 */
public class UpdateMessageStatusHandler {
    private static final Logger logger = LoggerFactory.getLogger(UpdateMessageStatusHandler.class);
    private final MessageStatusRepository msRepo = new MessageStatusRepository();
    private final ConversationRepository convRepo = new ConversationRepository();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long userId = conn.getUserId();
            if (userId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }

            if (!request.has("status")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing status field");
                return response;
            }

            MessageStatus.Status targetStatus = MessageStatus.Status.valueOf(request.get("status").getAsString());

            if (request.has("messageId")) {
                long messageId = request.get("messageId").getAsLong();
                long conversationId = request.has("conversationId") ? request.get("conversationId").getAsLong() : 0;
                
                msRepo.update(messageId, userId, targetStatus);
                logger.info("[UPDATE_STATUS] MessageId={} | UserId={} | Status updated to {}", messageId, userId, targetStatus);

                MessageStatus.Status collectiveStatus = msRepo.getCollectiveStatus(messageId);

                JsonObject event = new JsonObject();
                event.addProperty("action", "MESSAGE_STATUS_EVENT");
                event.addProperty("messageId", messageId);
                if (conversationId > 0) {
                    event.addProperty("conversationId", conversationId);
                }
                event.addProperty("status", collectiveStatus.name());
                event.addProperty("userId", userId);

                if (conversationId > 0) {
                    List<Long> memberIds = convRepo.getMemberIds(conversationId);
                    for (Long memberId : memberIds) {
                        if (!memberId.equals(userId)) {
                            TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
                        }
                    }
                }
            } else if (request.has("conversationId")) {
                long conversationId = request.get("conversationId").getAsLong();
                if (targetStatus == MessageStatus.Status.SEEN) {
                    msRepo.markAllAsSeen(conversationId, userId);
                    logger.info("[UPDATE_STATUS] ConversationId={} | UserId={} | All marked as SEEN", conversationId, userId);

                    List<Long> memberIds = convRepo.getMemberIds(conversationId);
                    JsonObject event = new JsonObject();
                    event.addProperty("action", "MESSAGE_STATUS_EVENT");
                    event.addProperty("conversationId", conversationId);
                    event.addProperty("status", "SEEN");
                    event.addProperty("userId", userId);

                    for (Long memberId : memberIds) {
                        if (!memberId.equals(userId)) {
                            TcpConnectionManager.getInstance().broadcastToUser(memberId, event);
                        }
                    }
                }
            }

            response.addProperty("status", "success");
        } catch (Exception e) {
            logger.error("[UPDATE_STATUS ERROR] Error: {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
