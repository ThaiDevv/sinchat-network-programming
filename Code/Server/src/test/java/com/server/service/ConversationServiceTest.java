package com.server.service;

import com.google.gson.JsonArray;
import com.server.model.Conversation;
import com.server.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;

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

        // Should NOT create a new conversation
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
}
