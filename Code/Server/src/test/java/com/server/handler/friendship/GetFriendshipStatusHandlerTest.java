package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFriendshipStatusHandlerTest {

    private GetFriendshipStatusHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new GetFriendshipStatusHandler(mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        return conn;
    }

    @Test
    void testMissingTargetUserId() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testGetStatusAccepted() {
        when(mockService.getFriendshipStatus(1L, 2L)).thenReturn("ACCEPTED");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("ACCEPTED", resp.get("friendshipStatus").getAsString());
    }

    @Test
    void testGetStatusNone() {
        when(mockService.getFriendshipStatus(-1L, 2L)).thenReturn("NONE");

        JsonObject req = new JsonObject();
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(null); // defaults to -1L

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("NONE", resp.get("friendshipStatus").getAsString());
    }
}
