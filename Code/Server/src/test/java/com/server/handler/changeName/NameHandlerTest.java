package com.server.handler.changeName;

import com.google.gson.JsonObject;
import com.server.service.UserNameService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NameHandlerTest {

    private NameHandler handler;
    private UserNameService mockService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new NameHandler();
        mockService = mock(UserNameService.class);

        Field field = NameHandler.class.getDeclaredField("userNameService");
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
        req.addProperty("newUsername", "newname");
        JsonObject resp = handler.handle(conn, req);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Thiếu"));

        // Missing newUsername
        req = new JsonObject();
        req.addProperty("userId", 1L);
        resp = handler.handle(conn, req);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testUnauthorizedUserIdMismatch() {
        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("newUsername", "newname");
        ClientConnection conn = createMockConn(2L); // conn has different userId

        JsonObject resp = handler.handle(conn, req);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Unauthorized: userId mismatch", resp.get("message").getAsString());
    }

    @Test
    void testUnauthorizedNotAuthenticated() {
        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("newUsername", "newname");
        ClientConnection conn = createMockConn(null); // not authenticated

        JsonObject resp = handler.handle(conn, req);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Unauthorized: userId mismatch", resp.get("message").getAsString());
    }

    @Test
    void testChangeNameSuccess() {
        when(mockService.updateUsername(1L, "newname")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("newUsername", "newname");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handle(conn, req);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("newname", resp.get("newUsername").getAsString());
    }

    @Test
    void testChangeNameServiceFailure() {
        when(mockService.updateUsername(1L, "newname")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("userId", 1L);
        req.addProperty("newUsername", "newname");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handle(conn, req);
        assertEquals("error", resp.get("status").getAsString());
    }
}
