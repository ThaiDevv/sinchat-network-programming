package com.server.service;

import com.google.gson.JsonArray;
import com.server.model.Conversation;
import com.server.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationServiceTest {

    private ConversationService conversationService;
    private ConversationRepository mockRepo;

    @BeforeEach
    void setUp() throws Exception {
        conversationService = new ConversationService();
        mockRepo = mock(ConversationRepository.class);

        Field repoField = ConversationService.class.getDeclaredField("conversationRepo");
        repoField.setAccessible(true);
        repoField.set(conversationService, mockRepo);
    }

    @Test
    void testGetOrCreateExistingConversation() throws SQLException {
        when(mockRepo.findPrivateConversation(1L, 2L)).thenReturn(42L);

        long result = conversationService.getOrCreatePrivateConversation(1L, 2L);
        assertEquals(42L, result);

        verify(mockRepo, never()).createConversation(any(), anyLong());
        verify(mockRepo, never()).addMember(anyLong(), anyLong());
    }

    @Test
    void testGetOrCreateNewConversation() throws SQLException {
        when(mockRepo.findPrivateConversation(1L, 2L)).thenReturn(null);
        when(mockRepo.createConversation(Conversation.ConversationType.PRIVATE, 1L)).thenReturn(99L);

        long result = conversationService.getOrCreatePrivateConversation(1L, 2L);
        assertEquals(99L, result);

        verify(mockRepo).createConversation(Conversation.ConversationType.PRIVATE, 1L);
        verify(mockRepo).addMember(99L, 1L);
        verify(mockRepo).addMember(99L, 2L);
    }

    @Test
    void testGetOrCreateSQLException() throws SQLException {
        when(mockRepo.findPrivateConversation(1L, 2L)).thenReturn(null);
        when(mockRepo.createConversation(any(), anyLong())).thenThrow(new SQLException("DB error"));

        assertThrows(SQLException.class, () ->
            conversationService.getOrCreatePrivateConversation(1L, 2L)
        );
    }

    @Test
    void testGetConversationsWithDetails() {
        JsonArray expected = new JsonArray();
        when(mockRepo.getConversationsWithDetails(5L)).thenReturn(expected);

        JsonArray result = conversationService.getConversationsWithDetails(5L);
        assertNotNull(result);
        assertSame(expected, result);
    }

    // =============== GROUP MANAGEMENT TESTS ===============

    @Test
    void testCreateGroupConversation() throws SQLException {
        when(mockRepo.createGroupConversation(1L, "Group Name", Arrays.asList(1L, 2L, 3L))).thenReturn(100L);

        long result = conversationService.createGroupConversation(1L, "Group Name", Arrays.asList(1L, 2L, 3L));
        assertEquals(100L, result);
    }

    @Test
    void testLeaveGroupConversation() throws SQLException {
        conversationService.leaveGroupConversation(5L, 1L);
        verify(mockRepo).removeMember(5L, 1L);
    }

    @Test
    void testGetConversationType() {
        when(mockRepo.getConversationType(5L)).thenReturn("GROUP");
        String type = conversationService.getConversationType(5L);
        assertEquals("GROUP", type);
    }

    @Test
    void testGetConversationTypeNotFound() {
        when(mockRepo.getConversationType(999L)).thenReturn(null);
        assertNull(conversationService.getConversationType(999L));
    }

    @Test
    void testGetMemberRole() {
        when(mockRepo.getMemberRole(5L, 1L)).thenReturn("CREATOR");
        String role = conversationService.getMemberRole(5L, 1L);
        assertEquals("CREATOR", role);
    }

    @Test
    void testGetConversationCreator() {
        when(mockRepo.getConversationCreator(5L)).thenReturn(1L);
        long creator = conversationService.getConversationCreator(5L);
        assertEquals(1L, creator);
    }

    @Test
    void testGetConversationCreatorNull() {
        when(mockRepo.getConversationCreator(999L)).thenReturn(null);
        long creator = conversationService.getConversationCreator(999L);
        assertEquals(-1L, creator);
    }

    @Test
    void testGetGroupMembers() {
        JsonArray expected = new JsonArray();
        when(mockRepo.getMembersWithDetails(5L)).thenReturn(expected);
        JsonArray members = conversationService.getGroupMembers(5L);
        assertSame(expected, members);
    }

    @Test
    void testRenameGroup() throws SQLException {
        conversationService.renameGroup(5L, "New Name");
        verify(mockRepo).updateGroupName(5L, "New Name");
    }

    @Test
    void testAddGroupMember() throws SQLException {
        conversationService.addGroupMember(5L, 3L);
        verify(mockRepo).addMemberWithRole(5L, 3L, "MEMBER");
    }

    @Test
    void testKickGroupMember() throws SQLException {
        conversationService.kickGroupMember(5L, 3L);
        verify(mockRepo).removeMember(5L, 3L);
    }

    @Test
    void testTransferGroupAdmin() throws SQLException {
        conversationService.transferGroupAdmin(5L, 1L, 2L);
        verify(mockRepo).transferAdmin(5L, 1L, 2L);
    }

    @Test
    void testDisbandGroup() throws SQLException {
        conversationService.disbandGroup(5L);
        verify(mockRepo).disbandGroup(5L);
    }

    @Test
    void testIsGroupMemberTrue() {
        when(mockRepo.isGroupMember(5L, 1L)).thenReturn(true);
        assertTrue(conversationService.isGroupMember(5L, 1L));
    }

    @Test
    void testIsGroupMemberFalse() {
        when(mockRepo.isGroupMember(5L, 999L)).thenReturn(false);
        assertFalse(conversationService.isGroupMember(5L, 999L));
    }

    @Test
    void testGetMemberIds() {
        when(mockRepo.getMemberIds(5L)).thenReturn(Arrays.asList(1L, 2L, 3L));
        assertEquals(3, conversationService.getMemberIds(5L).size());
    }

    @Test
    void testGetMemberIdsEmpty() {
        when(mockRepo.getMemberIds(5L)).thenReturn(Collections.emptyList());
        assertTrue(conversationService.getMemberIds(5L).isEmpty());
    }

    @Test
    void testGetConversationName() {
        when(mockRepo.getConversationName(5L)).thenReturn("My Group");
        assertEquals("My Group", conversationService.getConversationName(5L));
    }
}

