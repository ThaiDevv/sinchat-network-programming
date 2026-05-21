package com.server.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.server.ProfileHandler;
import com.server.handler.changeavatar.AvatarHandler;
import com.server.service.AvatarService;
import com.server.tcp.TcpServer;
import com.server.tcp.Router;
import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for additional TCP actions/endpoints:
 * PROFILE, CHANGE_AVATAR, JOIN, and TYPING broadcast.
 */
class AdditionalEndpointsIntegrationTest {

    private static TcpServer server;
    private static int port;
    private static AvatarService mockAvatarService;
    private static Connection mockConn;
    private static PreparedStatement mockStmt;
    private static ResultSet mockRs;

    @BeforeAll
    static void startServer() throws Exception {
        mockAvatarService = mock(AvatarService.class);
        mockConn = mock(Connection.class);
        mockStmt = mock(PreparedStatement.class);
        mockRs = mock(ResultSet.class);

        // Subclass ProfileHandler to override getConnection and return mock connection
        ProfileHandler customProfileHandler = new ProfileHandler() {
            @Override
            protected Connection getConnection() throws java.sql.SQLException {
                return mockConn;
            }
        };

        // Inject custom profile handler and mock avatar service into Router
        injectField(Router.class, "profileHandler", customProfileHandler);

        AvatarHandler avatarHandler = (AvatarHandler) getStaticField(Router.class, "avatarHandler");
        injectField(avatarHandler, "avatarService", mockAvatarService);

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
    void resetMocks() throws Exception {
        reset(mockAvatarService, mockConn, mockStmt, mockRs);
        // Default stubbing for mock Connection to always return mock Statement
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
    }

    private static Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void injectField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

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

    // ──────────────────── PROFILE (GET_PROFILE) Tests ────────────────────

    @Test
    void getProfileSuccess() throws Exception {
        when(mockStmt.executeQuery()).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString("username")).thenReturn("alice");
        when(mockRs.getString("email")).thenReturn("alice@test.com");
        when(mockRs.getString("avatar_url")).thenReturn("avatar.png");

        JsonObject req = new JsonObject();
        req.addProperty("action", "PROFILE");
        req.addProperty("subAction", "GET_PROFILE");
        req.addProperty("userId", 42);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("alice", resp.get("username").getAsString());
        assertEquals("alice@test.com", resp.get("email").getAsString());
    }

    @Test
    void getProfileMissingUserIdReturnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "PROFILE");
        req.addProperty("subAction", "GET_PROFILE");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing userId"));
    }

    // ──────────────────── PROFILE (UPDATE_PROFILE) Tests ────────────────────

    @Test
    void updateProfileSuccess() throws Exception {
        when(mockStmt.executeUpdate()).thenReturn(1);

        JsonObject req = new JsonObject();
        req.addProperty("action", "PROFILE");
        req.addProperty("subAction", "UPDATE_PROFILE");
        req.addProperty("userId", 42);
        req.addProperty("email", "alice@new.com");
        req.addProperty("avatar_url", "new_avatar_url.png");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("updated successfully"));
    }

    // ──────────────────── CHANGE_AVATAR Tests ────────────────────

    @Test
    void changeAvatarSuccess() throws Exception {
        when(mockAvatarService.changeAvatar(42, "new_avatar.png")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("action", "CHANGE_AVATAR");
        req.addProperty("userId", 42);
        req.addProperty("avatarUrl", "new_avatar.png");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("new_avatar.png", resp.get("avatarUrl").getAsString());
    }

    @Test
    void changeAvatarMissingFields() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "CHANGE_AVATAR");
        req.addProperty("userId", 42);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ──────────────────── JOIN Endpoint Tests ────────────────────

    @Test
    void joinSuccess() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "JOIN");
        req.addProperty("userId", 100);

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
    }

    // ──────────────────── TYPING Broadcast Tests ────────────────────

    @Test
    void typingEventBroadcastSuccess() throws Exception {
        // Setup two client connections to verify live broadcast
        try (Socket socketUser1 = new Socket("localhost", port);
             PrintWriter out1 = new PrintWriter(new OutputStreamWriter(socketUser1.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in1 = new BufferedReader(new InputStreamReader(socketUser1.getInputStream(), StandardCharsets.UTF_8));
             
             Socket socketUser2 = new Socket("localhost", port);
             PrintWriter out2 = new PrintWriter(new OutputStreamWriter(socketUser2.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in2 = new BufferedReader(new InputStreamReader(socketUser2.getInputStream(), StandardCharsets.UTF_8))) {

            // Step 1: User 1 JOINS as userId 101
            JsonObject join1 = new JsonObject();
            join1.addProperty("action", "JOIN");
            join1.addProperty("userId", 101);
            out1.println(join1.toString());
            String join1Resp = in1.readLine();
            assertNotNull(join1Resp);

            // Step 2: User 2 JOINS as userId 102
            JsonObject join2 = new JsonObject();
            join2.addProperty("action", "JOIN");
            join2.addProperty("userId", 102);
            out2.println(join2.toString());
            String join2Resp = in2.readLine();
            assertNotNull(join2Resp);

            // Step 3: User 1 sends TYPING event aimed at memberId 102
            JsonObject typingReq = new JsonObject();
            typingReq.addProperty("action", "TYPING");
            typingReq.addProperty("conversationId", 200L);
            typingReq.addProperty("memberId", 102L);
            typingReq.addProperty("isTyping", true);
            out1.println(typingReq.toString());

            // Step 4: User 2 should receive the broadcasted TYPING_EVENT live
            String broadcastEventLine = in2.readLine();
            assertNotNull(broadcastEventLine);

            JsonObject broadcastEvent = JsonParser.parseString(broadcastEventLine).getAsJsonObject();
            assertEquals("TYPING_EVENT", broadcastEvent.get("action").getAsString());
            assertEquals(200, broadcastEvent.get("conversationId").getAsLong());
            assertEquals(101, broadcastEvent.get("userId").getAsLong());
            assertTrue(broadcastEvent.get("isTyping").getAsBoolean());
        }
    }
}
