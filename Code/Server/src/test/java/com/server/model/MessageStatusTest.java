package com.server.model;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class MessageStatusTest {

    @Test
    void testDefaultConstructor() {
        MessageStatus ms = new MessageStatus();
        assertEquals(0, ms.getMessageId());
        assertEquals(0, ms.getUserId());
        assertNull(ms.getStatus());
        assertNull(ms.getUpdatedAt());
    }

    @Test
    void testParameterizedConstructor() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        MessageStatus ms = new MessageStatus(1L, 2L, MessageStatus.Status.SENT, now);

        assertEquals(1L, ms.getMessageId());
        assertEquals(2L, ms.getUserId());
        assertEquals(MessageStatus.Status.SENT, ms.getStatus());
        assertEquals(now, ms.getUpdatedAt());
    }

    @Test
    void testSettersAndGetters() {
        MessageStatus ms = new MessageStatus();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        ms.setMessageId(10L);
        ms.setUserId(20L);
        ms.setStatus(MessageStatus.Status.DELIVERED);
        ms.setUpdatedAt(now);

        assertEquals(10L, ms.getMessageId());
        assertEquals(20L, ms.getUserId());
        assertEquals(MessageStatus.Status.DELIVERED, ms.getStatus());
        assertEquals(now, ms.getUpdatedAt());
    }

    @Test
    void testStatusEnum() {
        assertEquals(3, MessageStatus.Status.values().length);
        assertEquals(MessageStatus.Status.SENT, MessageStatus.Status.valueOf("SENT"));
        assertEquals(MessageStatus.Status.DELIVERED, MessageStatus.Status.valueOf("DELIVERED"));
        assertEquals(MessageStatus.Status.SEEN, MessageStatus.Status.valueOf("SEEN"));
    }

    @Test
    void testStatusProgression() {
        MessageStatus ms = new MessageStatus();
        ms.setStatus(MessageStatus.Status.SENT);
        assertEquals(MessageStatus.Status.SENT, ms.getStatus());

        ms.setStatus(MessageStatus.Status.DELIVERED);
        assertEquals(MessageStatus.Status.DELIVERED, ms.getStatus());

        ms.setStatus(MessageStatus.Status.SEEN);
        assertEquals(MessageStatus.Status.SEEN, ms.getStatus());
    }
}
