package com.server.tcp;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TcpConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(TcpConnectionManager.class);
    private static TcpConnectionManager instance;
    private final ConcurrentHashMap<Long, Set<ClientConnection>> userConnections = new ConcurrentHashMap<>();
    private final Set<ClientConnection> activeConnections = ConcurrentHashMap.newKeySet();

    private TcpConnectionManager() {}

    public static synchronized TcpConnectionManager getInstance() {
        if (instance == null) {
            instance = new TcpConnectionManager();
        }
        return instance;
    }

    public void addConnection(Long userId, ClientConnection conn) {
        activeConnections.add(conn);
        userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conn);
        logger.info("[CMGR ADD] UserId={} | Remote={} | ActiveConnections={}",
                userId, conn.getRemoteAddress(), activeConnections.size());
    }

    public void removeConnection(ClientConnection conn) {
        activeConnections.remove(conn);
        logger.info("[CMGR REMOVE] Remote={} | UserId={} | ActiveConnectionsRemaining={}",
                conn.getRemoteAddress(), conn.getUserId(), activeConnections.size());
        if (conn.getUserId() != null) {
            Set<ClientConnection> conns = userConnections.get(conn.getUserId());
            if (conns != null) {
                conns.remove(conn);
                logger.info("[CMGR REMOVE] UserId={} | ConnectionsForUserRemaining={}",
                        conn.getUserId(), conns.size());
                if (conns.isEmpty()) {
                    userConnections.remove(conn.getUserId());
                    logger.info("[CMGR REMOVE] UserId={} - Removed user entry entirely (no more connections)",
                            conn.getUserId());
                }
            }
        }
    }

    public void broadcastToUser(Long userId, JsonObject message) {
        Set<ClientConnection> conns = userConnections.get(userId);
        if (conns != null && !conns.isEmpty()) {
            logger.info("[CMGR BROADCAST] TargetUserId={} | Action={} | ConnectionCount={}",
                    userId, message.get("action"), conns.size());
            for (ClientConnection c : conns) {
                c.send(message);
            }
        } else {
            logger.warn("[CMGR BROADCAST] TargetUserId={} | Action={} - User has no active connections",
                    userId, message.get("action"));
        }
    }

    public Set<ClientConnection> getActiveConnectionsSnapshot() {
        return Set.copyOf(activeConnections);
    }

    public boolean hasOnlineConnection(long userId) {
        Set<ClientConnection> conns = userConnections.get(userId);
        return conns != null && !conns.isEmpty();
    }
}
