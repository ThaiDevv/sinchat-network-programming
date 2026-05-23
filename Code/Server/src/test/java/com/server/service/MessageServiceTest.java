package com.server.service;

import com.server.model.Message;
import com.server.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    private MessageService messageService;
    private MessageRepository mockRepo;

    @BeforeEach
    void setUp() throws Exception {
        messageService = new MessageService();
        mockRepo = mock(MessageRepository.class);

        Field repoField = MessageService.class.getDeclaredField("messageRepository");
        repoField.setAccessible(true);
        repoField.set(messageService, mockRepo);
    }

    @Test
    void testSendMessageSuccess() throws SQLException {
        when(mockRepo.save(any(Message.class))).thenReturn(100L);

        long result = messageService.sendMessage(1L, 5L, "Hello world!");
        assertEquals(100L, result);
        verify(mockRepo).save(any(Message.class));
    }

    @Test
    void testSendMessageVerifiesFields() throws SQLException {
        when(mockRepo.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            assertEquals(10L, msg.getConversationId());
            assertEquals(3L, msg.getSenderId());
            assertEquals(Message.MessageType.TEXT, msg.getType());
            assertEquals("Test message", msg.getContent());
            return 1L;
        });

        messageService.sendMessage(10L, 3L, "Test message");
    }

    @Test
    void testSendMessageSQLException() throws SQLException {
        when(mockRepo.save(any(Message.class))).thenThrow(new SQLException("DB error"));

        assertThrows(SQLException.class, () ->
            messageService.sendMessage(1L, 1L, "content")
        );
    }

    @Test
    void testGetMessagesReturnsMessages() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        List<Message> expected = Arrays.asList(
            new Message(1L, 10L, 1L, Message.MessageType.TEXT, "Hi", now),
            new Message(2L, 10L, 2L, Message.MessageType.TEXT, "Hello", now)
        );
        when(mockRepo.getByConversationId(10L)).thenReturn(expected);

        List<Message> result = messageService.getMessages(10L);
        assertEquals(2, result.size());
        assertEquals("Hi", result.get(0).getContent());
        assertEquals("Hello", result.get(1).getContent());
    }

    @Test
    void testGetMessagesEmptyConversation() {
        when(mockRepo.getByConversationId(99L)).thenReturn(Collections.emptyList());

        List<Message> result = messageService.getMessages(99L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
