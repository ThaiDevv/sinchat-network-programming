package com.server.handler.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.service.ConversationService;
import com.server.tcp.ClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupManagementHandlerTest {

    private GroupManagementHandler handler;
    private ConversationService mockService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GroupManagementHandler();
        mockService = mock(ConversationService.class);

        Field field = GroupManagementHandler.class.getDeclaredField("conversationService");
        field.setAccessible(true);
        field.set(handler, mockService);
    }

    private ClientConnection createMockConn(Long userId) {
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.getUserId()).thenReturn(userId);
        when(conn.getRemoteAddress()).thenReturn("127.0.0.1:12345");
        return conn;
    }

    private JsonObject baseReq(long conversationId, String subAction) {
        JsonObject req = new JsonObject();
        req.addProperty("conversationId", conversationId);
        req.addProperty("subAction", subAction);
        return req;
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    void testMissingFields() {
        ClientConnection conn = createMockConn(1L);

        // Missing conversationId
        JsonObject req = new JsonObject();
        req.addProperty("subAction", "GET_MEMBERS");
        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());

        // Missing subAction
        req = new JsonObject();
        req.addProperty("conversationId", 1L);
        resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testNotAuthenticated() {
        JsonObject req = baseReq(1L, "GET_MEMBERS");
        ClientConnection conn = createMockConn(null);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Not authenticated", resp.get("message").getAsString());
    }

    @Test
    void testConversationNotFound() {
        when(mockService.getConversationType(1L)).thenReturn(null);

        JsonObject req = baseReq(1L, "GET_MEMBERS");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Conversation not found", resp.get("message").getAsString());
    }

    @Test
    void testNotAGroup() {
        when(mockService.getConversationType(1L)).thenReturn("PRIVATE");

        JsonObject req = baseReq(1L, "GET_MEMBERS");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("Not a group conversation", resp.get("message").getAsString());
    }

    @Test
    void testUserNotMember() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 5L)).thenReturn(false);

        JsonObject req = baseReq(1L, "GET_MEMBERS");
        ClientConnection conn = createMockConn(5L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
        assertEquals("You are not a member of this group", resp.get("message").getAsString());
    }

    @Test
    void testUnknownSubAction() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonObject req = baseReq(1L, "UNKNOWN_ACTION");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ==================== GET_MEMBERS ====================

    @Test
    void testGetMembersSuccess() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonArray members = new JsonArray();
        JsonObject m1 = new JsonObject();
        m1.addProperty("userId", 1L);
        m1.addProperty("role", "CREATOR");
        members.add(m1);
        JsonObject m2 = new JsonObject();
        m2.addProperty("userId", 2L);
        m2.addProperty("role", "MEMBER");
        members.add(m2);

        when(mockService.getGroupMembers(1L)).thenReturn(members);
        when(mockService.getMemberRole(1L, 1L)).thenReturn("CREATOR");
        when(mockService.getConversationName(1L)).thenReturn("Test Group");
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "GET_MEMBERS");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(2, resp.get("members").getAsJsonArray().size());
        assertEquals("CREATOR", resp.get("myRole").getAsString());
        assertEquals("Test Group", resp.get("groupName").getAsString());
        assertEquals(1L, resp.get("createdBy").getAsLong());
    }

    // ==================== RENAME ====================

    @Test
    void testRenameSuccess() throws Exception {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getMemberIds(1L)).thenReturn(Arrays.asList(1L, 2L));

        JsonObject req = baseReq(1L, "RENAME");
        req.addProperty("newName", "New Group Name");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("New Group Name", resp.get("newName").getAsString());
    }

    @Test
    void testRenameMissingNewName() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonObject req = baseReq(1L, "RENAME");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testRenameEmptyName() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonObject req = baseReq(1L, "RENAME");
        req.addProperty("newName", "   ");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testRenameNameTooLong() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonObject req = baseReq(1L, "RENAME");
        req.addProperty("newName", "a".repeat(101));
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ==================== ADD_MEMBER ====================

    @Test
    void testAddMemberSuccess() throws Exception {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.isGroupMember(1L, 3L)).thenReturn(false);
        when(mockService.getMemberIds(1L)).thenReturn(Arrays.asList(1L, 2L));

        JsonObject req = baseReq(1L, "ADD_MEMBER");
        req.addProperty("targetUserId", 3L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(3L, resp.get("addedUserId").getAsLong());
    }

    @Test
    void testAddMemberMissingTarget() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);

        JsonObject req = baseReq(1L, "ADD_MEMBER");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testAddMemberAlreadyInGroup() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);

        JsonObject req = baseReq(1L, "ADD_MEMBER");
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ==================== KICK_MEMBER ====================

    @Test
    void testKickMemberSuccess() throws Exception {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);
        when(mockService.getMemberIds(1L)).thenReturn(Arrays.asList(1L, 2L));

        JsonObject req = baseReq(1L, "KICK_MEMBER");
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(2L, resp.get("kickedUserId").getAsLong());
    }

    @Test
    void testKickMemberNotCreator() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "KICK_MEMBER");
        req.addProperty("targetUserId", 3L);
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testKickSelf() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "KICK_MEMBER");
        req.addProperty("targetUserId", 1L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ==================== TRANSFER_ADMIN ====================

    @Test
    void testTransferAdminSuccess() throws Exception {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);
        when(mockService.getMemberIds(1L)).thenReturn(Arrays.asList(1L, 2L));

        JsonObject req = baseReq(1L, "TRANSFER_ADMIN");
        req.addProperty("targetUserId", 2L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(2L, resp.get("newAdminId").getAsLong());
    }

    @Test
    void testTransferAdminNotCreator() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "TRANSFER_ADMIN");
        req.addProperty("targetUserId", 3L);
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testTransferAdminToSelf() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "TRANSFER_ADMIN");
        req.addProperty("targetUserId", 1L);
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testTransferAdminMissingTarget() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "TRANSFER_ADMIN");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }

    // ==================== DISBAND ====================

    @Test
    void testDisbandSuccess() throws Exception {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 1L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);
        when(mockService.getMemberIds(1L)).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(mockService.getConversationName(1L)).thenReturn("Test Group");

        JsonObject req = baseReq(1L, "DISBAND");
        ClientConnection conn = createMockConn(1L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testDisbandNotCreator() {
        when(mockService.getConversationType(1L)).thenReturn("GROUP");
        when(mockService.isGroupMember(1L, 2L)).thenReturn(true);
        when(mockService.getConversationCreator(1L)).thenReturn(1L);

        JsonObject req = baseReq(1L, "DISBAND");
        ClientConnection conn = createMockConn(2L);

        JsonObject resp = handler.handleTcp(req, conn);
        assertEquals("error", resp.get("status").getAsString());
    }
}
