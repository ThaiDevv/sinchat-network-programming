package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SendFriendRequestHandlerTest {

    private SendFriendRequestHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new SendFriendRequestHandler(mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        return conn;
    }

    @Test
    void testUnauthorized() {
        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Unauthorized", resp.get("message").getAsString());
    }

    @Test
    void testMissingTargetUserId() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    @Test
    void testSendSuccess() {
        when(mockService.sendFriendRequest(1L, 2L)).thenReturn("sent");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testAlreadyFriends() {
        when(mockService.sendFriendRequest(1L, 2L)).thenReturn("already_friends");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testPendingSent() {
        when(mockService.sendFriendRequest(1L, 2L)).thenReturn("pending_sent");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testPendingReceived() {
        when(mockService.sendFriendRequest(1L, 2L)).thenReturn("pending_received");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testBlocked() {
        when(mockService.sendFriendRequest(1L, 2L)).thenReturn("blocked");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testSendToSelf() {
        when(mockService.sendFriendRequest(1L, 1L)).thenReturn("self");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 1L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }
}
