package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.service.MessageService;
import com.server.websocket.ChatWebSocket;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class SendMessageHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageHandler.class);
    private final Gson gson = new Gson();
    private final MessageService messageService = new MessageService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (!json.has("conversationId") || !json.has("senderId") || !json.has("content")) {
                sendResponse(exchange, 400, "{\"error\": \"Missing required fields: conversationId, senderId, content\"}");
                return;
            }

            long conversationId = json.get("conversationId").getAsLong();
            long senderId = json.get("senderId").getAsLong();
            String content = json.get("content").getAsString();

            if (content.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"error\": \"Content cannot be empty\"}");
                return;
            }

            long messageId = messageService.sendMessage(conversationId, senderId, content);

            if (messageId > 0) {
                ChatWebSocket wsServer = ChatWebSocket.getInstance();
                if (wsServer != null) {
                    String createdAt = new java.sql.Timestamp(System.currentTimeMillis()).toString();
                    wsServer.broadcastNewMessage(messageId, conversationId, senderId, content, createdAt);
                }

                sendResponse(exchange, 200, "{\"status\": \"success\", \"messageId\": " + messageId + "}");
            } else {
                sendResponse(exchange, 400, "{\"error\": \"Failed to send message\"}");
            }
        } catch (Exception e) {
            logger.error("Send message error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] response = body.getBytes();
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
