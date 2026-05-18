package com.server.tcp;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TcpConnectionManager {
    private static TcpConnectionManager instance;
    private final ConcurrentHashMap<Long, Set<ClientConnection>> userConnections = new ConcurrentHashMap<>();

    private TcpConnectionManager() {}

    public static synchronized TcpConnectionManager getInstance() {
        if (instance == null) {
            instance = new TcpConnectionManager();
        }
        return instance;
    }

    public void addConnection(Long userId, ClientConnection conn) {
        userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conn);
    }

    public void removeConnection(ClientConnection conn) {
        if (conn.getUserId() != null) {
            Set<ClientConnection> conns = userConnections.get(conn.getUserId());
            if (conns != null) {
                conns.remove(conn);
                if (conns.isEmpty()) {
                    userConnections.remove(conn.getUserId());
                }
            }
        }
    }

    public void broadcastToUser(Long userId, JsonObject message) {
        Set<ClientConnection> conns = userConnections.get(userId);
        if (conns != null) {
            for (ClientConnection c : conns) {
                c.send(message);
            }
        }
    }
}
