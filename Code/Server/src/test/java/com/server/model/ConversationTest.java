package com.server.model;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

    @Test
    void testDefaultConstructor() {
        Conversation conv = new Conversation();
        assertEquals(0, conv.getId());
        assertNull(conv.getType());
        assertNull(conv.getName());
        assertNull(conv.getAvatarUrl());
        assertNull(conv.getCreatedBy());
        assertNull(conv.getCreatedAt());
        assertNull(conv.getLastMessageAt());
    }

    @Test
    void testParameterizedConstructor() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Conversation conv = new Conversation(1L, Conversation.ConversationType.PRIVATE,
                "Chat Room", "http://img.com/room.png", 5L, now, now);

        assertEquals(1L, conv.getId());
        assertEquals(Conversation.ConversationType.PRIVATE, conv.getType());
        assertEquals("Chat Room", conv.getName());
        assertEquals("http://img.com/room.png", conv.getAvatarUrl());
        assertEquals(5L, conv.getCreatedBy());
        assertEquals(now, conv.getCreatedAt());
        assertEquals(now, conv.getLastMessageAt());
    }

    @Test
    void testSettersAndGetters() {
        Conversation conv = new Conversation();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        conv.setId(10L);
        conv.setType(Conversation.ConversationType.GROUP);
        conv.setName("Study Group");
        conv.setAvatarUrl("/avatars/group.png");
        conv.setCreatedBy(3L);
        conv.setCreatedAt(now);
        conv.setLastMessageAt(now);

        assertEquals(10L, conv.getId());
        assertEquals(Conversation.ConversationType.GROUP, conv.getType());
        assertEquals("Study Group", conv.getName());
        assertEquals("/avatars/group.png", conv.getAvatarUrl());
        assertEquals(3L, conv.getCreatedBy());
        assertEquals(now, conv.getCreatedAt());
        assertEquals(now, conv.getLastMessageAt());
    }

    @Test
    void testConversationTypeEnum() {
        assertEquals(2, Conversation.ConversationType.values().length);
        assertEquals(Conversation.ConversationType.PRIVATE, Conversation.ConversationType.valueOf("PRIVATE"));
        assertEquals(Conversation.ConversationType.GROUP, Conversation.ConversationType.valueOf("GROUP"));
    }

    @Test
    void testNullCreatedBy() {
        Conversation conv = new Conversation();
        conv.setCreatedBy(null);
        assertNull(conv.getCreatedBy());
    }
}
