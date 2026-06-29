package com.server.handler.friendship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.FriendshipService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFriendsHandlerTest {

    private GetFriendsHandler handler;
    private FriendshipService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(FriendshipService.class);
        handler = new GetFriendsHandler(mockService);
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
    void testGetFriendsSuccess() {
        JsonArray friends = new JsonArray();
        JsonObject friend = new JsonObject();
        friend.addProperty("userId", 2L);
        friend.addProperty("username", "friend1");
        friends.add(friend);

        when(mockService.getFriendList(1L)).thenReturn(friends);

        JsonObject req = new JsonObject();
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(1, resp.get("friends").getAsJsonArray().size());
    }
}
