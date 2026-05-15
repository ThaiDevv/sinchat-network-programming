package com.server.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatWebSocket extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocket.class);
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());

    public ChatWebSocket(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        logger.info("New WebSocket connection from: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        logger.info("WebSocket connection closed: {} | code={} reason={}", conn.getRemoteSocketAddress(), code, reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.info("Message received from {}: {}", conn.getRemoteSocketAddress(), message);
        // Broadcast message to all connected clients
        synchronized (connections) {
            for (WebSocket client : connections) {
                if (client.isOpen()) {
                    client.send(message);
                }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error on connection {}: ", conn != null ? conn.getRemoteSocketAddress() : "unknown", ex);
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on port {}", getPort());
        setConnectionLostTimeout(60);
    }
}
