package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.service.MessageService;
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
            Long conversationId = (json.has("conversationId") && !json.get("conversationId").isJsonNull()) 
                                  ? json.get("conversationId").getAsLong() : null;
            long senderId = json.get("senderId").getAsLong();
            
            Long receiverId = (json.has("receiverId") && !json.get("receiverId").isJsonNull()) 
                                ? json.get("receiverId").getAsLong() : null;
            
            String content = json.has("content") ? json.get("content").getAsString() : "";
            if (content == null || content.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Content cannot be empty\"}");
                return;
            }

            if (conversationId == null && receiverId == null) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Missing conversationId or receiverId\"}");
                return;
            }

            long messageId = messageService.sendMessage(conversationId, senderId, receiverId, content);
            
            if (messageId > 0) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"messageId\": " + messageId + "}");
            } else {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Failed to send message\"}");
            }
        } catch (Exception e) {
            logger.error("Send message error", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error: " + e.getMessage() + "\"}");
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
