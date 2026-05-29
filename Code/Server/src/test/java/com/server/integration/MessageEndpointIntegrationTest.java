package com.server.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.server.handler.message.GetMessagesHandler;
import com.server.handler.message.SendMessageHandler;
import com.server.handler.message.ConversationHandle;
import com.server.handler.message.GetConversationsHandler;
import com.server.service.MessageService;
import com.server.service.ConversationService;
import com.server.model.Message;
import com.server.tcp.TcpServer;
import com.server.tcp.Router;
import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for message/conversation TCP actions.
 * Spins up a real TcpServer with real handlers but mocked services
 * to test the full TCP request/response JSON cycle.
 */
class MessageEndpointIntegrationTest {

    private static TcpServer server;
    private static int port;
    private static MessageService mockMsgService;
    private static ConversationService mockConvService;

    @BeforeAll
    static void startServer() throws Exception {
        mockMsgService = mock(MessageService.class);
        mockConvService = mock(ConversationService.class);

        GetMessagesHandler getMessagesHandler = (GetMessagesHandler) getStaticField(Router.class, "getMessagesHandler");
        ConversationHandle conversationHandle = (ConversationHandle) getStaticField(Router.class, "conversationHandle");
        GetConversationsHandler getConversationsHandler = (GetConversationsHandler) getStaticField(Router.class, "getConversationsHandler");

        // Subclass SendMessageHandler to avoid database dependency
        SendMessageHandler sendMessageHandler = new SendMessageHandler() {
            @Override
            protected java.util.List<Long> getMemberIds(long conversationId) {
                return java.util.Arrays.asList(1L, 2L);
            }
        };

        injectField(getMessagesHandler, "messageService", mockMsgService);
        injectField(sendMessageHandler, "messageService", mockMsgService);
        injectField(Router.class, "sendMessageHandler", sendMessageHandler); // Inject custom handler into Router

        injectField(conversationHandle, "conversationService", mockConvService);
        injectField(getConversationsHandler, "conversationService", mockConvService);

        server = new TcpServer(0); // Ephemeral port
        server.start();

        int retries = 0;
        while (server.getPort() == 0 && retries < 10) {
            Thread.sleep(100);
            retries++;
        }
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(mockMsgService, mockConvService);
    }

    private static Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        Field field = null;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void injectField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    // ──────────────── Helpers ─────────────────────────

    /**
     * Sends a request on a new socket and closes it immediately.
     * Suitable for stateless actions (e.g. GET_MESSAGES).
     */
    private JsonObject sendTcpRequest(JsonObject requestJson) throws IOException {
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            
            out.println(requestJson.toString());
            String responseLine = in.readLine();
            if (responseLine == null) {
                return null;
            }
            return JsonParser.parseString(responseLine).getAsJsonObject();
        }
    }

    /**
     * A persistent TCP session that authenticates via JOIN on creation.
     * Use for actions that require an authenticated connection (e.g. SEND_MESSAGE).
     */
    private static class TcpSession implements AutoCloseable {
        final Socket socket;
        final PrintWriter out;
        final BufferedReader in;

        TcpSession(long userId) throws IOException {
            this.socket = new Socket("localhost", port);
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            JsonObject joinReq = new JsonObject();
            joinReq.addProperty("action", "JOIN");
            joinReq.addProperty("userId", userId);
            out.println(joinReq.toString());
            String joinResp = in.readLine();
            if (joinResp == null) {
                throw new IOException("No JOIN response received");
            }
        }

        JsonObject send(JsonObject request) throws IOException {
            out.println(request.toString());
            String responseLine = in.readLine();
            if (responseLine == null) return null;
            return JsonParser.parseString(responseLine).getAsJsonObject();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    // ──────────────── GET_MESSAGES Action ────────────────

    @Test
    void getMessagesSuccess() throws Exception {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(mockMsgService.getMessages(10L)).thenReturn(Arrays.asList(
                new Message(1L, 10L, 1L, Message.MessageType.TEXT, "Hello", now),
                new Message(2L, 10L, 2L, Message.MessageType.TEXT, "Hi back", now)
        ));

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");
        req.addProperty("conversationId", 10);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.toString().contains("Hello"));
        assertTrue(resp.toString().contains("Hi back"));
        assertEquals(2, resp.get("count").getAsInt());
    }

    @Test
    void getMessagesEmptyConversation() throws Exception {
        when(mockMsgService.getMessages(99L)).thenReturn(Collections.emptyList());

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");
        req.addProperty("conversationId", 99);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(0, resp.get("count").getAsInt());
    }

    @Test
    void getMessagesMissingConversationId() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_MESSAGES");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    // ──────────────── SEND_MESSAGE Action ────────────────

    @Test
    void sendMessageSuccess() throws Exception {
        when(mockMsgService.sendMessage(1L, 5L, "Hello world")).thenReturn(100L);

        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", 1);
        req.addProperty("senderId", 5);
        req.addProperty("content", "Hello world");

        try (TcpSession session = new TcpSession(5L)) {
            JsonObject resp = session.send(req);
            assertNotNull(resp);
            assertEquals("success", resp.get("status").getAsString());
            assertEquals(100, resp.get("messageId").getAsLong());
        }
    }

    @Test
    void sendMessageMissingFields() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", 1);

        try (TcpSession session = new TcpSession(1L)) {
            JsonObject resp = session.send(req);
            assertNotNull(resp);
            assertEquals("error", resp.get("status").getAsString());
            assertTrue(resp.get("message").getAsString().contains("Missing"));
        }
    }

    @Test
    void sendMessageEmptyContent() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "SEND_MESSAGE");
        req.addProperty("conversationId", 1);
        req.addProperty("senderId", 5);
        req.addProperty("content", "   ");

        try (TcpSession session = new TcpSession(5L)) {
            JsonObject resp = session.send(req);
            assertNotNull(resp);
            assertEquals("error", resp.get("status").getAsString());
            assertTrue(resp.get("message").getAsString().contains("Message content is required"));
        }
    }

    // ──────────── GET_OR_CREATE_CONVERSATION Action ────────────

    @Test
    void getOrCreateConversationSuccess() throws Exception {
        when(mockConvService.getOrCreatePrivateConversation(1L, 2L)).thenReturn(42L);

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_OR_CREATE_CONVERSATION");
        req.addProperty("user1Id", 1);
        req.addProperty("user2Id", 2);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(42, resp.get("conversationId").getAsLong());
    }

    @Test
    void getOrCreateConversationMissingFields() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_OR_CREATE_CONVERSATION");
        req.addProperty("user1Id", 1);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    // ──────────── GET_USER_CONVERSATIONS Action ────────────

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

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_CONVERSATIONS");
        req.addProperty("userId", 5);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.toString().contains("Alice"));
        assertTrue(resp.toString().contains("Hey!"));
    }

    @Test
    void getUserConversationsEmpty() throws Exception {
        when(mockConvService.getConversationsWithDetails(99L)).thenReturn(new JsonArray());

        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_CONVERSATIONS");
        req.addProperty("userId", 99);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(0, resp.get("conversations").getAsJsonArray().size());
    }

    @Test
    void getUserConversationsMissingUserId() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "GET_USER_CONVERSATIONS");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    // ──────────── Full Client Message Flow ────────────

    @Test
    void fullSendAndRetrieveMessageFlow() throws Exception {
        // Step 1: Create/get conversation
        when(mockConvService.getOrCreatePrivateConversation(1L, 2L)).thenReturn(10L);
        JsonObject convReq = new JsonObject();
        convReq.addProperty("action", "GET_OR_CREATE_CONVERSATION");
        convReq.addProperty("user1Id", 1);
        convReq.addProperty("user2Id", 2);

        JsonObject convResp = sendTcpRequest(convReq);
        assertNotNull(convResp);
        assertEquals("success", convResp.get("status").getAsString());
        assertEquals(10, convResp.get("conversationId").getAsLong());

        // Step 2: Send message in conversation (requires authenticated session)
        when(mockMsgService.sendMessage(10L, 1L, "Test message")).thenReturn(50L);
        JsonObject sendReq = new JsonObject();
        sendReq.addProperty("action", "SEND_MESSAGE");
        sendReq.addProperty("conversationId", 10);
        sendReq.addProperty("senderId", 1);
        sendReq.addProperty("content", "Test message");

        try (TcpSession session = new TcpSession(1L)) {
            JsonObject sendResp = session.send(sendReq);
            assertNotNull(sendResp);
            assertEquals("success", sendResp.get("status").getAsString());
            assertEquals(50, sendResp.get("messageId").getAsLong());
        }

        // Step 3: Retrieve messages
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(mockMsgService.getMessages(10L)).thenReturn(
                Arrays.asList(new Message(50L, 10L, 1L, Message.MessageType.TEXT, "Test message", now))
        );
        JsonObject getReq = new JsonObject();
        getReq.addProperty("action", "GET_MESSAGES");
        getReq.addProperty("conversationId", 10);

        JsonObject getResp = sendTcpRequest(getReq);
        assertNotNull(getResp);
        assertEquals("success", getResp.get("status").getAsString());
        assertTrue(getResp.toString().contains("Test message"));
        assertEquals(1, getResp.get("count").getAsInt());
    }
}
