package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.model.Message;
import com.server.service.MessageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class GetMessagesHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetMessagesHandler.class);
    private final Gson gson = new Gson();
    private final MessageService messageService = new MessageService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
            return;
        }

        try {
            String rawQuery = exchange.getRequestURI().getQuery();
            Long conversationId = parseConversationId(rawQuery);
            if (conversationId == null || conversationId <= 0) {
                sendResponse(exchange, 400,
                    "{\"status\": \"error\", \"message\": \"Missing or invalid conversationId\"}");
                return;
            }
            List<Message> messages = messageService.getMessages(conversationId);

            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("count", messages.size());
            response.add("messages", gson.toJsonTree(messages));

            sendResponse(exchange, 200, gson.toJson(response));

        } catch (NumberFormatException e) {
            sendResponse(exchange, 400,
                "{\"status\": \"error\", \"message\": \"conversationId must be a number\"}");
        } catch (Exception e) {
            logger.error("Get messages error", e);
            sendResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"Internal Server Error\"}");
        }
    }

    private Long parseConversationId(String query) {
        if (query == null || query.isEmpty()) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "conversationId".equals(pair[0])) {
                return Long.parseLong(pair[1]);
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
