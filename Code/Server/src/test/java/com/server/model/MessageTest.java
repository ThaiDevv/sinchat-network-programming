package com.server.model;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testDefaultConstructor() {
        Message msg = new Message();
        assertEquals(0, msg.getId());
        assertEquals(0, msg.getConversationId());
        assertEquals(0, msg.getSenderId());
        assertNull(msg.getType());
        assertNull(msg.getContent());
        assertNull(msg.getCreatedAt());
    }

    @Test
    void testParameterizedConstructor() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Message msg = new Message(1L, 10L, 5L, Message.MessageType.TEXT, "Hello!", now);

        assertEquals(1L, msg.getId());
        assertEquals(10L, msg.getConversationId());
        assertEquals(5L, msg.getSenderId());
        assertEquals(Message.MessageType.TEXT, msg.getType());
        assertEquals("Hello!", msg.getContent());
        assertEquals(now, msg.getCreatedAt());
    }

    @Test
    void testSettersAndGetters() {
        Message msg = new Message();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        msg.setId(99L);
        msg.setConversationId(20L);
        msg.setSenderId(3L);
        msg.setType(Message.MessageType.IMAGE);
        msg.setContent("http://img.com/pic.png");
        msg.setCreatedAt(now);

        assertEquals(99L, msg.getId());
        assertEquals(20L, msg.getConversationId());
        assertEquals(3L, msg.getSenderId());
        assertEquals(Message.MessageType.IMAGE, msg.getType());
        assertEquals("http://img.com/pic.png", msg.getContent());
        assertEquals(now, msg.getCreatedAt());
    }

    @Test
    void testMessageTypeEnum() {
        assertEquals(6, Message.MessageType.values().length);
        assertEquals(Message.MessageType.TEXT, Message.MessageType.valueOf("TEXT"));
        assertEquals(Message.MessageType.IMAGE, Message.MessageType.valueOf("IMAGE"));
        assertEquals(Message.MessageType.VIDEO, Message.MessageType.valueOf("VIDEO"));
        assertEquals(Message.MessageType.VOICE, Message.MessageType.valueOf("VOICE"));
        assertEquals(Message.MessageType.FILE, Message.MessageType.valueOf("FILE"));
        assertEquals(Message.MessageType.SYSTEM, Message.MessageType.valueOf("SYSTEM"));
    }

    @Test
    void testAllMessageTypes() {
        Message msg = new Message();
        for (Message.MessageType type : Message.MessageType.values()) {
            msg.setType(type);
            assertEquals(type, msg.getType());
        }
    }
}
