package com.server.tcp;

import com.google.gson.JsonObject;
import com.server.handler.auth.*;
import com.server.handler.message.*;
import com.server.handler.changeavatar.*;
import com.server.ProfileHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Router {
    private static final Logger logger = LoggerFactory.getLogger(Router.class);

    // Reuse handler instances to optimize GC overhead and facilitate injection in testing
    private static LoginHandler loginHandler = new LoginHandler();
    private static RegisterHandler registerHandler = new RegisterHandler();
    private static ForgotPasswordHandler forgotPasswordHandler = new ForgotPasswordHandler();
    private static ProfileHandler profileHandler = new ProfileHandler();
    private static GetMessagesHandler getMessagesHandler = new GetMessagesHandler();
    private static SendMessageHandler sendMessageHandler = new SendMessageHandler();
    private static ConversationHandle conversationHandle = new ConversationHandle();
    private static GetConversationsHandler getConversationsHandler = new GetConversationsHandler();
    private static SearchUserHandler searchUserHandler = new SearchUserHandler();
    private static AvatarHandler avatarHandler = new AvatarHandler();
    private static final PresenceService presenceService = PresenceService.getInstance();

    public static void route(JsonObject request, ClientConnection conn) {
        if (!request.has("action")) {
            conn.sendError("Missing action field");
            return;
        }

        conn.markActive();

        String action = request.get("action").getAsString();
        String requestId = request.has("requestId") ? request.get("requestId").getAsString() : null;

        try {
            JsonObject response = null;
            switch (action) {
                case "LOGIN":
                    response = loginHandler.handleTcp(request, conn);
                    break;
                case "REGISTER":
                    response = registerHandler.handleTcp(request, conn);
                    break;
                case "FORGOT_PASSWORD":
                    response = forgotPasswordHandler.handleTcp(request, conn);
                    break;
                case "PROFILE":
                    response = profileHandler.handleTcp(request, conn);
                    break;
                case "GET_MESSAGES":
                    response = getMessagesHandler.handleTcp(request, conn);
                    break;
                case "SEND_MESSAGE":
                    response = sendMessageHandler.handleTcp(request, conn);
                    break;
                case "GET_OR_CREATE_CONVERSATION":
                    response = conversationHandle.handleTcp(request, conn);
                    break;
                case "GET_USER_CONVERSATIONS":
                    response = getConversationsHandler.handleTcp(request, conn);
                    break;
                case "SEARCH_USERS":
                    response = searchUserHandler.handleTcp(request, conn);
                    break;
                case "CHANGE_AVATAR":
                    response = avatarHandler.handleTcp(request, conn);
                    break;
                case "JOIN":
                    if (request.has("userId")) {
                        long userId = request.get("userId").getAsLong();
                        Long previousUserId = conn.getUserId();
                        if (previousUserId != null && previousUserId != userId) {
                            TcpConnectionManager.getInstance().removeConnection(conn);
                            if (!TcpConnectionManager.getInstance().hasOnlineConnection(previousUserId)) {
                                presenceService.onUserOffline(previousUserId);
                            }
                        }
                        conn.setUserId(userId);
                        TcpConnectionManager.getInstance().addConnection(userId, conn);
                        presenceService.onUserOnline(userId);
                        response = new JsonObject();
                        response.addProperty("status", "success");
                        response.addProperty("message", "Joined successfully");
                    }
                    break;
                case "PING":
                    response = new JsonObject();
                    response.addProperty("action", "PING_RESPONSE");
                    response.addProperty("status", "success");
                    if (requestId != null) {
                        response.addProperty("requestId", requestId);
                    }
                    conn.send(response);
                    return;
                case "TYPING":
                    // Broadcast typing status to the target member or members in the conversation in real-time.
                    if (!request.has("conversationId") || !request.has("isTyping")) {
                        conn.sendError("Missing required fields for TYPING");
                        return;
                    }

                    Long fromUserId = conn.getUserId();
                    if (fromUserId == null) {
                        conn.sendError("Not joined (missing userId). Send JOIN first");
                        return;
                    }

                    long conversationId = request.get("conversationId").getAsLong();
                    boolean isTyping = request.get("isTyping").getAsBoolean();

                    JsonObject evt = new JsonObject();
                    evt.addProperty("action", "TYPING_EVENT");
                    evt.addProperty("conversationId", conversationId);
                    evt.addProperty("userId", fromUserId);
                    evt.addProperty("isTyping", isTyping);

                    if (request.has("memberId")) {
                        long memberId = request.get("memberId").getAsLong();
                        TcpConnectionManager.getInstance().broadcastToUser(memberId, evt);
                    } else {
                        // Find all other members in the conversation and broadcast to them
                        java.util.List<Long> memberIds = new com.server.repository.ConversationRepository().getMemberIds(conversationId);
                        for (Long memberId : memberIds) {
                            if (!memberId.equals(fromUserId)) {
                                TcpConnectionManager.getInstance().broadcastToUser(memberId, evt);
                            }
                        }
                    }

                    // Send a lightweight ACK (sender side typically ignores it; safe for clients that expect a response).
                    response = new JsonObject();
                    response.addProperty("status", "success");
                    response.addProperty("message", "Typing event broadcasted");
                    break;
                default:
                    conn.sendError("Unknown action: " + action);
                    return;
            }

            if (response != null) {
                if (!response.has("action")) {
                    response.addProperty("action", action + "_RESPONSE");
                }
                if (requestId != null && !response.has("requestId")) {
                    response.addProperty("requestId", requestId);
                }
                conn.send(response);
            }
        } catch (Throwable e) {
            logger.error("Error handling action " + action, e);
            JsonObject err = new JsonObject();
            err.addProperty("action", action + "_RESPONSE");
            if (requestId != null) err.addProperty("requestId", requestId);
            err.addProperty("status", "error");
            err.addProperty("message", "Internal server error: " + e.getMessage());
            conn.send(err);
        }
    }
}
