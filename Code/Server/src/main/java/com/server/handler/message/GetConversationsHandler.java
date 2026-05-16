package com.server.handler.message;

import com.google.gson.JsonArray;
import com.server.service.ConversationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class GetConversationsHandler implements HttpHandler {
    private final ConversationService conversationService = new ConversationService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        try {
            // Extract userId from query parameter: ?userId=1
            String query = exchange.getRequestURI().getQuery();
            long userId = -1;
            if (query != null && query.contains("userId=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("userId=")) {
                        userId = Long.parseLong(param.substring("userId=".length()));
                        break;
                    }
                }
            }

            if (userId == -1) {
                sendResponse(exchange, 400, "{\"error\": \"Missing or invalid userId\"}");
                return;
            }

            JsonArray array = conversationService.getConversationsWithDetails(userId);
            String response = "{\"status\": \"success\", \"data\": " + array.toString() + "}";
            sendResponse(exchange, 200, response);
            
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}
