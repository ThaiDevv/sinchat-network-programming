package com.server.handler.changeavatar;

import com.google.gson.JsonObject;
import com.server.service.AvatarService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvatarHandlerTest {

    private AvatarHandler handler;
    private AvatarService mockService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new AvatarHandler();
        mockService = mock(AvatarService.class);

        Field field = AvatarHandler.class.getDeclaredField("avatarService");
        field.setAccessible(true);
        field.set(handler, mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        when(conn.getRemoteAddress()).thenReturn("127.0.0.1:12345");
        return conn;
    }

    @Test
    void testMissingFields() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        // Missing userId
        req.addProperty("avatarUrl", "data:image/png;base64,abc");
        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));

        // Missing avatarUrl
        req = new JsonObject();
        req.addProperty("userId", 1L);
        resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testUnauthorizedUserIdMismatch() {
        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("avatarUrl", "data:image/png;base64,abc");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Unauthorized: userId mismatch", resp.get("message").getAsString());
    }

    @Test
    void testUnauthorizedNotAuthenticated() {
        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("avatarUrl", "data:image/png;base64,abc");
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Unauthorized: userId mismatch", resp.get("message").getAsString());
    }

    @Test
    void testChangeAvatarSuccess() {
        when(mockService.changeAvatar(1L, "data:image/png;base64,abc")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("avatarUrl", "data:image/png;base64,abc");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("success"));
    }

    @Test
    void testChangeAvatarServiceFailure() {
        when(mockService.changeAvatar(1L, "data:image/png;base64,abc")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("avatarUrl", "data:image/png;base64,abc");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Failed"));
    }
}
