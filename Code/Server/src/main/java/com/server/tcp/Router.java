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
    private static AvatarHandler avatarHandler = new AvatarHandler();

    public static void route(JsonObject request, ClientConnection conn) {
        if (!request.has("action")) {
            conn.sendError("Missing action field");
            return;
        }

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
                case "CHANGE_AVATAR":
                    response = avatarHandler.handleTcp(request, conn);
                    break;
                case "JOIN":
                    if (request.has("userId")) {
                        long userId = request.get("userId").getAsLong();
                        conn.setUserId(userId);
                        TcpConnectionManager.getInstance().addConnection(userId, conn);
                        response = new JsonObject();
                        response.addProperty("status", "success");
                        response.addProperty("message", "Joined successfully");
                    }
                    break;
                case "TYPING":
                    if (request.has("conversationId") && request.has("memberId") && request.has("isTyping")) {
                        long conversationId = request.get("conversationId").getAsLong();
                        long memberId = request.get("memberId").getAsLong();
                        boolean isTyping = request.get("isTyping").getAsBoolean();
                        
                        JsonObject typingEvent = new JsonObject();
                        typingEvent.addProperty("action", "TYPING_EVENT");
                        typingEvent.addProperty("conversationId", conversationId);
                        typingEvent.addProperty("userId", conn.getUserId());
                        typingEvent.addProperty("isTyping", isTyping);
                        TcpConnectionManager.getInstance().broadcastToUser(memberId, typingEvent);
                    }
                    return;
                default:
                    conn.sendError("Unknown action: " + action);
                    return;
            }

            if (response != null) {
                response.addProperty("action", action + "_RESPONSE");
                if (requestId != null) {
                    response.addProperty("requestId", requestId);
                }
                conn.send(response);
            }
        } catch (Exception e) {
            logger.error("Error handling action " + action, e);
            JsonObject err = new JsonObject();
            err.addProperty("action", action + "_RESPONSE");
            if (requestId != null) err.addProperty("requestId", requestId);
            err.addProperty("status", "error");
            err.addProperty("message", "Internal server error");
            conn.send(err);
        }
    }
}
