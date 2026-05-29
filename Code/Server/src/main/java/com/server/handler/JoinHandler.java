package com.server.handler;

import com.google.gson.JsonObject;
import com.server.tcp.ClientConnection;
import com.server.tcp.PresenceService;
import com.server.tcp.TcpConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoinHandler {
    private static final Logger logger = LoggerFactory.getLogger(JoinHandler.class);
    private static final PresenceService presenceService = PresenceService.getInstance();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        if (!request.has("userId")) {
            logger.warn("[ROUTER JOIN] Remote={} - Missing 'userId' field in JOIN request",
                    conn.getRemoteAddress());
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", "Missing userId");
            return error;
        }

        long userId = request.get("userId").getAsLong();
        Long previousUserId = conn.getUserId();

        logger.info("[JOIN] Remote={} | Joining with userId={} | PreviousUserId={}",
                conn.getRemoteAddress(), userId, previousUserId);

        // Must handle account switch (remove from old user) BEFORE changing conn.userId,
        // because removeConnection() reads conn.getUserId() to know which user to remove from.
        if (previousUserId != null && previousUserId != userId) {
            logger.info("[JOIN] Remote={} | Switching from userId={} to userId={} - removing old connection first",
                    conn.getRemoteAddress(), previousUserId, userId);
            TcpConnectionManager.getInstance().removeConnection(conn);
            if (!TcpConnectionManager.getInstance().hasOnlineConnection(previousUserId)) {
                logger.info("[JOIN] Remote={} | Previous userId={} has no more connections, broadcasting offline",
                        conn.getRemoteAddress(), previousUserId);
                presenceService.onUserOffline(previousUserId);
            }
        }

        conn.setUserId(userId);
        TcpConnectionManager.getInstance().addConnection(userId, conn);

        if (previousUserId == null) {
            logger.info("[JOIN] Remote={} | userId={} - New login, broadcasting online status",
                    conn.getRemoteAddress(), userId);
            presenceService.onUserOnline(userId);
        } else {
            logger.info("[JOIN] Remote={} | userId={} - Re-JOIN (previousUserId={}), skipping online broadcast",
                    conn.getRemoteAddress(), userId, previousUserId);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "success");
        response.addProperty("message", "Joined successfully");
        return response;
    }
}
