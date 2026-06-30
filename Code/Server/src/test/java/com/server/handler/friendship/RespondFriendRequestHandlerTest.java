package com.server.handler.friendship;

import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespondFriendRequestHandlerTest {

    private RespondFriendRequestHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new RespondFriendRequestHandler(mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        return conn;
    }

    @Test
    void testUnauthorized() {
        JsonObject req = new JsonObject();
        req.addProperty("requesterId", 1L);
        req.addProperty("decision", "ACCEPTED");
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testMissingFields() {
        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testInvalidDecision() {
        JsonObject req = new JsonObject();
        req.addProperty("requesterId", 1L);
        req.addProperty("decision", "INVALID");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Invalid"));
    }

    @Test
    void testAcceptSuccess() {
        when(mockService.respondToRequest(2L, 1L, "ACCEPTED")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("requesterId", 1L);
        req.addProperty("decision", "ACCEPTED");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testRejectSuccess() {
        when(mockService.respondToRequest(2L, 1L, "REJECTED")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("requesterId", 1L);
        req.addProperty("decision", "REJECTED");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testRequestNotFound() {
        when(mockService.respondToRequest(2L, 999L, "ACCEPTED")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("requesterId", 999L);
        req.addProperty("decision", "ACCEPTED");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }
}
