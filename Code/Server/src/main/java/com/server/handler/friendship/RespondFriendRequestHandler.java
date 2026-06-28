package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.repository.UserRepository;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles RESPOND_FRIEND_REQUEST action.
 * decision: "ACCEPTED" or "REJECTED"
 * On ACCEPTED, pushes FRIEND_ACCEPTED_EVENT to the original sender.
 */
public class RespondFriendRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(RespondFriendRequestHandler.class);
    private final FriendshipService friendshipService;
    private final UserRepository userRepository;

    public RespondFriendRequestHandler(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
        this.userRepository = new UserRepository();
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            Long responderId = conn.getUserId();
            if (responderId == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Unauthorized");
                return response;
            }
            if (!request.has("requesterId") || !request.has("decision")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing requesterId or decision");
                return response;
            }

            long requesterId = request.get("requesterId").getAsLong();
            String decision = request.get("decision").getAsString().toUpperCase();

            if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) {
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid decision. Use ACCEPTED or REJECTED");
                return response;
            }

            boolean ok = friendshipService.respondToRequest(responderId, requesterId, decision);
            logger.info("[RESPOND_FRIEND_REQUEST] responderId={} requesterId={} decision={} ok={}", responderId,
                    requesterId, decision, ok);

            if (ok) {
                response.addProperty("status", "success");
                if ("ACCEPTED".equals(decision)) {
                    response.addProperty("message", "Đã chấp nhận lời mời kết bạn");
                    // Push real-time FRIEND_ACCEPTED_EVENT den sender
                    pushFriendAcceptedEvent(responderId, requesterId);
                } else {
                    response.addProperty("message", "Đã từ chối lời mời kết bạn");
                }
            } else {
                response.addProperty("status", "error");
                response.addProperty("message", "Không tìm thấy lời mời kết bạn");
            }
        } catch (Exception e) {
            logger.error("[RESPOND_FRIEND_REQUEST ERROR] {}", e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }

    private void pushFriendAcceptedEvent(long acceptorId, long originalSenderId) {
        try {
            com.server.model.User acceptor = userRepository.findById(acceptorId);
            String acceptorName = (acceptor != null) ? acceptor.getUsername() : "Ai đó";

            JsonObject event = new JsonObject();
            event.addProperty("action", "FRIEND_ACCEPTED_EVENT");
            event.addProperty("acceptorId", acceptorId);
            event.addProperty("acceptorName", acceptorName);
            TcpConnectionManager.getInstance().broadcastToUser(originalSenderId, event);
            logger.info("[RESPOND_FRIEND_REQUEST] Pushed FRIEND_ACCEPTED_EVENT to userId={}", originalSenderId);
        } catch (Exception e) {
            logger.warn("[RESPOND_FRIEND_REQUEST] Failed to push event to userId={}: {}", originalSenderId,
                    e.getMessage());
        }
    }
}
