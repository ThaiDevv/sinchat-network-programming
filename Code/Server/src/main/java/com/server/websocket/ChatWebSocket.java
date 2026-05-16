package com.server.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.server.model.Message;
import com.server.repository.ConversationRepository;
import com.server.service.MessageService;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWebSocket extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocket.class);
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<Long, Set<WebSocket>> userConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, Long> connectionUserMap = new ConcurrentHashMap<>();
    private final MessageService messageService = new MessageService();
    private final ConversationRepository conversationRepo = new ConversationRepository();
    private static ChatWebSocket instance;
    public ChatWebSocket(InetSocketAddress address) {
        super(address);
        instance = this;
    }

    public static ChatWebSocket getInstance() {
        return instance;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("New WebSocket connection from: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Long userId = connectionUserMap.remove(conn);
        if (userId != null) {
            Set<WebSocket> conns = userConnections.get(userId);
            if (conns != null) {
                conns.remove(conn);
                if (conns.isEmpty()) {
                    userConnections.remove(userId);
                }
            }
            logger.info("User {} disconnected (code={}, reason={})", userId, code, reason);
        } else {
            logger.info("Anonymous WebSocket disconnected: {}", conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String action = json.has("action") ? json.get("action").getAsString() : "";

            switch (action) {
                case "join" -> handleJoin(conn, json);
                case "send_message" -> handleSendMessage(conn, json);
                case "typing" -> handleTyping(conn, json);
                default -> {
                    logger.warn("Unknown action '{}' from {}", action, conn.getRemoteSocketAddress());
                    sendError(conn, "Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing WebSocket message from {}: {}",
                    conn.getRemoteSocketAddress(), message, e);
            sendError(conn, "Invalid message format");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error on connection {}: ",
                conn != null ? conn.getRemoteSocketAddress() : "unknown", ex);
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on port {}", getPort());
        setConnectionLostTimeout(60);
    }
    private void handleJoin(WebSocket conn, JsonObject json) {
        if (!json.has("userId")) {
            sendError(conn, "Missing userId in join");
            return;
        }
        long userId = json.get("userId").getAsLong();

        connectionUserMap.put(conn, userId);
        userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conn);

        logger.info("User {} joined via WebSocket", userId);

        JsonObject response = new JsonObject();
        response.addProperty("action", "joined");
        response.addProperty("userId", userId);
        conn.send(gson.toJson(response));
    }

    private void handleSendMessage(WebSocket conn, JsonObject json) {
        if (!json.has("conversationId") || !json.has("senderId") || !json.has("content")) {
            sendError(conn, "Missing required fields: conversationId, senderId, content");
            return;
        }

        long conversationId = json.get("conversationId").getAsLong();
        long senderId = json.get("senderId").getAsLong();
        String content = json.get("content").getAsString();

        if (content.trim().isEmpty()) {
            sendError(conn, "Content cannot be empty");
            return;
        }

        try {
            long messageId = messageService.sendMessage(conversationId, senderId, content);

            if (messageId <= 0) {
                sendError(conn, "Failed to save message");
                return;
            }
            List<Long> memberIds = conversationRepo.getMemberIds(conversationId);


            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("action", "new_message");
            broadcast.addProperty("messageId", messageId);
            broadcast.addProperty("conversationId", conversationId);
            broadcast.addProperty("senderId", senderId);
            broadcast.addProperty("content", content);
            broadcast.addProperty("createdAt", new java.sql.Timestamp(System.currentTimeMillis()).toString());
            String payload = gson.toJson(broadcast);
            broadcastToMembers(memberIds, payload);
            logger.info("Message {} sent in conversation {} by user {}", messageId, conversationId, senderId);
        } catch (SQLException e) {
            logger.error("Error saving message", e);
            sendError(conn, "Database error");
        }
    }
    private void handleTyping(WebSocket conn, JsonObject json) {
        if (!json.has("conversationId") || !json.has("userId")) return;

        long conversationId = json.get("conversationId").getAsLong();
        long userId = json.get("userId").getAsLong();
        List<Long> memberIds = conversationRepo.getMemberIds(conversationId);

        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("action", "user_typing");
        broadcast.addProperty("conversationId", conversationId);
        broadcast.addProperty("userId", userId);
        String payload = gson.toJson(broadcast);
        for (Long memberId : memberIds) {
            if (memberId == userId) continue;
            Set<WebSocket> conns = userConnections.get(memberId);
            if (conns != null) {
                for (WebSocket c : conns) {
                    if (c.isOpen()) c.send(payload);
                }
            }
        }
    }


    public void broadcastNewMessage(long messageId, long conversationId, long senderId,
                                     String content, String createdAt) {
        List<Long> memberIds = conversationRepo.getMemberIds(conversationId);
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("action", "new_message");
        broadcast.addProperty("messageId", messageId);
        broadcast.addProperty("conversationId", conversationId);
        broadcast.addProperty("senderId", senderId);
        broadcast.addProperty("content", content);
        broadcast.addProperty("createdAt", createdAt);

        broadcastToMembers(memberIds, gson.toJson(broadcast));
    }

    private void broadcastToMembers(List<Long> memberIds, String payload) {
        for (Long memberId : memberIds) {
            Set<WebSocket> conns = userConnections.get(memberId);
            if (conns != null) {
                for (WebSocket c : conns) {
                    if (c.isOpen()) {
                        c.send(payload);
                    }
                }
            }
        }
    }


    private void sendError(WebSocket conn, String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("action", "error");
        error.addProperty("message", errorMessage);
        if (conn.isOpen()) {
            conn.send(gson.toJson(error));
        }
    }
}