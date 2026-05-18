package com.server.integration;

import com.server.handler.message.GetMessagesHandler;
import com.server.handler.message.SendMessageHandler;
import com.server.handler.message.ConversationHandle;
import com.server.handler.message.GetConversationsHandler;
import com.server.service.MessageService;
import com.server.service.ConversationService;
import com.server.model.Message;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for message/conversation endpoints.
 * These test the same HTTP paths the client's ChatApiClient calls.
 */
class MessageEndpointIntegrationTest {

    private static HttpServer server;
    private static int port;
    private static HttpClient client;
    private static MessageService mockMsgService;
    private static ConversationService mockConvService;

    @BeforeAll
    static void startServer() throws Exception {
        mockMsgService = mock(MessageService.class);
        mockConvService = mock(ConversationService.class);

        GetMessagesHandler getMessagesHandler = new GetMessagesHandler();
        injectField(getMessagesHandler, "messageService", mockMsgService);

        SendMessageHandler sendMessageHandler = new SendMessageHandler();
        injectField(sendMessageHandler, "messageService", mockMsgService);

        ConversationHandle convHandle = new ConversationHandle();
        injectField(convHandle, "conversationService", mockConvService);

        GetConversationsHandler getConvHandler = new GetConversationsHandler();
        injectField(getConvHandler, "conversationService", mockConvService);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/messages", getMessagesHandler);
        server.createContext("/api/messages/send", sendMessageHandler);
        server.createContext("/api/conversations/get-or-create", convHandle);
        server.createContext("/api/user/conversations", getConvHandler);
        server.setExecutor(null);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetMocks() {
        reset(mockMsgService, mockConvService);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // ──────────────── GET /api/messages ────────────────

    @Test
    void getMessagesSuccess() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(mockMsgService.getMessages(10L)).thenReturn(Arrays.asList(
                new Message(1L, 10L, 1L, Message.MessageType.TEXT, "Hello", now),
                new Message(2L, 10L, 2L, Message.MessageType.TEXT, "Hi back", now)
        ));

        HttpResponse<String> resp = sendGet("/api/messages?conversationId=10");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
        assertTrue(resp.body().contains("Hello"));
        assertTrue(resp.body().contains("Hi back"));
        assertTrue(resp.body().contains("\"count\":2"));
    }

    @Test
    void getMessagesEmptyConversation() throws Exception {
        when(mockMsgService.getMessages(99L)).thenReturn(Collections.emptyList());

        HttpResponse<String> resp = sendGet("/api/messages?conversationId=99");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"count\":0"));
    }

    @Test
    void getMessagesMissingConversationId() throws Exception {
        HttpResponse<String> resp = sendGet("/api/messages");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void getMessagesInvalidConversationId() throws Exception {
        HttpResponse<String> resp = sendGet("/api/messages?conversationId=abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void getMessagesPostMethodNotAllowed() throws Exception {
        HttpResponse<String> resp = sendPost("/api/messages", "{}");
        assertEquals(405, resp.statusCode());
    }

    @Test
    void getMessagesCorsOptions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/messages"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, resp.statusCode());
    }

    // ──────────────── POST /api/messages/send ────────────────

    @Test
    void sendMessageSuccess() throws Exception {
        when(mockMsgService.sendMessage(1L, 5L, "Hello world")).thenReturn(100L);

        HttpResponse<String> resp = sendPost("/api/messages/send",
                "{\"conversationId\":1,\"senderId\":5,\"content\":\"Hello world\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
        assertTrue(resp.body().contains("100"));
    }

    @Test
    void sendMessageMissingFields() throws Exception {
        HttpResponse<String> resp = sendPost("/api/messages/send",
                "{\"conversationId\":1}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void sendMessageEmptyContent() throws Exception {
        HttpResponse<String> resp = sendPost("/api/messages/send",
                "{\"conversationId\":1,\"senderId\":5,\"content\":\"   \"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void sendMessageGetMethodNotAllowed() throws Exception {
        HttpResponse<String> resp = sendGet("/api/messages/send");
        assertEquals(405, resp.statusCode());
    }

    @Test
    void sendMessageCorsOptions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/messages/send"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, resp.statusCode());
    }

    // ──────────── POST /api/conversations/get-or-create ────────────

    @Test
    void getOrCreateConversationSuccess() throws Exception {
        when(mockConvService.getOrCreatePrivateConversation(1L, 2L)).thenReturn(42L);

        HttpResponse<String> resp = sendPost("/api/conversations/get-or-create",
                "{\"user1Id\":1,\"user2Id\":2}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("42"));
        assertTrue(resp.body().contains("success"));
    }

    @Test
    void getOrCreateConversationMissingFields() throws Exception {
        HttpResponse<String> resp = sendPost("/api/conversations/get-or-create",
                "{\"user1Id\":1}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void getOrCreateConversationGetMethodNotAllowed() throws Exception {
        HttpResponse<String> resp = sendGet("/api/conversations/get-or-create");
        assertEquals(405, resp.statusCode());
    }

    @Test
    void getOrCreateConversationCorsOptions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/conversations/get-or-create"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, resp.statusCode());
    }

    // ──────────── GET /api/user/conversations ────────────

    @Test
    void getUserConversationsSuccess() throws Exception {
        JsonArray array = new JsonArray();
        JsonObject conv = new JsonObject();
        conv.addProperty("conversationId", 1L);
        conv.addProperty("type", "PRIVATE");
        conv.addProperty("displayName", "Alice");
        conv.addProperty("lastMessage", "Hey!");
        array.add(conv);
        when(mockConvService.getConversationsWithDetails(5L)).thenReturn(array);

        HttpResponse<String> resp = sendGet("/api/user/conversations?userId=5");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
        assertTrue(resp.body().contains("Alice"));
        assertTrue(resp.body().contains("Hey!"));
    }

    @Test
    void getUserConversationsEmpty() throws Exception {
        when(mockConvService.getConversationsWithDetails(99L)).thenReturn(new JsonArray());

        HttpResponse<String> resp = sendGet("/api/user/conversations?userId=99");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
    }

    @Test
    void getUserConversationsMissingUserId() throws Exception {
        HttpResponse<String> resp = sendGet("/api/user/conversations");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void getUserConversationsPostMethodNotAllowed() throws Exception {
        HttpResponse<String> resp = sendPost("/api/user/conversations", "{}");
        assertEquals(405, resp.statusCode());
    }

    // ──────────── Full Client Message Flow ────────────

    @Test
    void fullSendAndRetrieveMessageFlow() throws Exception {
        // Step 1: Create/get conversation
        when(mockConvService.getOrCreatePrivateConversation(1L, 2L)).thenReturn(10L);
        HttpResponse<String> convResp = sendPost("/api/conversations/get-or-create",
                "{\"user1Id\":1,\"user2Id\":2}");
        assertEquals(200, convResp.statusCode());
        assertTrue(convResp.body().contains("10"));

        // Step 2: Send message in conversation
        when(mockMsgService.sendMessage(10L, 1L, "Test message")).thenReturn(50L);
        HttpResponse<String> sendResp = sendPost("/api/messages/send",
                "{\"conversationId\":10,\"senderId\":1,\"content\":\"Test message\"}");
        assertEquals(200, sendResp.statusCode());
        assertTrue(sendResp.body().contains("50"));

        // Step 3: Retrieve messages
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(mockMsgService.getMessages(10L)).thenReturn(
                Arrays.asList(new Message(50L, 10L, 1L, Message.MessageType.TEXT, "Test message", now))
        );
        HttpResponse<String> getResp = sendGet("/api/messages?conversationId=10");
        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("Test message"));
        assertTrue(getResp.body().contains("\"count\":1"));
    }
}
