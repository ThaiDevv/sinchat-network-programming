package com.server.handler.friendship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFriendRequestsHandlerTest {

    private GetFriendRequestsHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new GetFriendRequestsHandler(mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        return conn;
    }

    @Test
    void testUnauthorized() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testGetRequestsSuccess() {
        JsonArray pending = new JsonArray();
        JsonArray sent = new JsonArray();
        JsonObject pendingItem = new JsonObject();
        pendingItem.addProperty("userId", 3L);
        pendingItem.addProperty("username", "requester");
        pending.add(pendingItem);

        when(mockService.getPendingRequests(1L)).thenReturn(pending);
        when(mockService.getSentRequests(1L)).thenReturn(sent);

        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(1, resp.get("pending").getAsJsonArray().size());
        assertEquals(0, resp.get("sent").getAsJsonArray().size());
    }
}
