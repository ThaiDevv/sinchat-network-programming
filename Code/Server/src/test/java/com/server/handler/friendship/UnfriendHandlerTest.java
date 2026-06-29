package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnfriendHandlerTest {

    private UnfriendHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new UnfriendHandler(mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        return conn;
    }

    @Test
    void testUnauthorized() {
        JsonObject req = new JsonObject();
        req.addProperty("friendId", 2L);
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testMissingFriendId() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testUnfriendSuccess() {
        when(mockService.unfriend(1L, 2L)).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("friendId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testUnfriendNotFound() {
        when(mockService.unfriend(1L, 2L)).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("friendId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testCancelRequestSuccess() {
        when(mockService.cancelFriendRequest(1L, 2L)).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("friendId", 2L);
        req.addProperty("subAction", "CANCEL_REQUEST");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testCancelRequestNotFound() {
        when(mockService.cancelFriendRequest(1L, 2L)).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("friendId", 2L);
        req.addProperty("subAction", "CANCEL_REQUEST");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }
}
