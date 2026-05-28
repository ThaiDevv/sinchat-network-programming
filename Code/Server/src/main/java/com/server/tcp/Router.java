package com.server.tcp;

import com.google.gson.JsonObject;
import com.server.handler.auth.*;
import com.server.handler.message.*;
import com.server.handler.changeavatar.*;
import com.server.handler.avatar.GetAvatarHandler;
import com.server.handler.JoinHandler;
import com.server.handler.PingHandler;
import com.server.handler.TypingHandler;
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
    private static GetAvatarHandler getAvatarHandler = new GetAvatarHandler();
    private static final JoinHandler joinHandler = new JoinHandler();
    private static final PingHandler pingHandler = new PingHandler();
    private static final TypingHandler typingHandler = new TypingHandler();
    public static void route(JsonObject request, ClientConnection conn) {
        if (!request.has("action")) {
            logger.warn("[ROUTER] Remote={} | Missing action field in request: {}",
                    conn.getRemoteAddress(), request);
            conn.sendError("Missing action field");
            return;
        }

        conn.markActive();

        String action = request.get("action").getAsString();
        String requestId = request.has("requestId") ? request.get("requestId").getAsString() : null;
        logger.info("[ROUTER] Dispatching action='{}' | Remote={} | UserId={} | requestId={}",
                action, conn.getRemoteAddress(), conn.getUserId(), requestId);

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
                case "GET_USER_PROFILE":
                    long getProfileUserId = request.get("userId").getAsLong();
                    logger.info("[GET_USER_PROFILE] Remote={} | UserId={} | Fetching user profile",
                            conn.getRemoteAddress(), getProfileUserId);
                    JsonObject profile = profileHandler.getUserProfile(request.get("userId").getAsLong());
                    if (profile != null) {
                        logger.info("[GET_USER_PROFILE] Remote={} | UserId={} | Profile found",
                                conn.getRemoteAddress(), getProfileUserId);
                        response = profile;
                    } else {
                        logger.warn("[GET_USER_PROFILE] Remote={} | UserId={} | Profile not found",
                                conn.getRemoteAddress(), getProfileUserId);
                        response = new JsonObject();
                        response.addProperty("status", "error");
                        response.addProperty("message", "User profile not found");
                    }
                    break;
                case "JOIN":
                    response = joinHandler.handleTcp(request, conn);
                    break;
                case "GET_AVATAR":
                    response = getAvatarHandler.handleTcp(request, conn);
                    break;
                case "PING":
                    pingHandler.handle(request, conn, requestId);
                    return; // PING already sent response
                case "TYPING":
                    response = typingHandler.handleTcp(request, conn);
                    break;
                default:
                    logger.warn("[ROUTER] Unknown action='{}' from Remote={} | UserId={}",
                            action, conn.getRemoteAddress(), conn.getUserId());
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
                logger.info("[ROUTER] Response sent for action='{}' | Remote={} | UserId={} | Status={}",
                        action, conn.getRemoteAddress(), conn.getUserId(),
                        response.has("status") ? response.get("status").getAsString() : "unknown");
            } else {
                logger.warn("[ROUTER] No response for action='{}' | Remote={} | UserId={}",
                        action, conn.getRemoteAddress(), conn.getUserId());
            }
        } catch (Throwable e) {
            logger.error("[ROUTER] Error handling action='{}' | Remote={} | UserId={} | Error: {}",
                    action, conn.getRemoteAddress(), conn.getUserId(), e.getMessage(), e);
            JsonObject err = new JsonObject();
            err.addProperty("action", action + "_RESPONSE");
            if (requestId != null) err.addProperty("requestId", requestId);
            err.addProperty("status", "error");
            err.addProperty("message", "Internal server error: " + e.getMessage());
            conn.send(err);
        }
    }
}
