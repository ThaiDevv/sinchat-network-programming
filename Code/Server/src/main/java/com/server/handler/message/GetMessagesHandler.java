package com.server.handler.message;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.model.Message;
import com.server.service.MessageService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TCP handler for retrieving messages of a conversation.
 */
public class GetMessagesHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetMessagesHandler.class);
    private final Gson gson = new Gson();
    private final MessageService messageService = new MessageService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("conversationId")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Missing conversationId");
                return response;
            }
            long conversationId = request.get("conversationId").getAsLong();
            List<Message> messages = messageService.getMessages(conversationId);

            response.addProperty("status", "success");
            response.addProperty("conversationId", conversationId);
            response.addProperty("count", messages.size());
            response.add("messages", gson.toJsonTree(messages));
        } catch (Exception e) {
            logger.error("Get messages error", e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
