package com.server.model;

import org.junit.jupiter.api.Test;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testDefaultConstructor() {
        User user = new User();
        assertEquals(0, user.getId());
        assertNull(user.getUsername());
        assertNull(user.getPasswordHash());
        assertNull(user.getEmail());
        assertNull(user.getAvatarUrl());
        assertNull(user.getStatusMessage());
        assertFalse(user.isOnline());
        assertNull(user.getLastSeen());
        assertNull(user.getCreatedAt());
    }

    @Test
    void testParameterizedConstructor() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        User user = new User(1L, "alice", "hash123", "alice@test.com",
                "http://img.com/avatar.png", "Hello!", true, now, now);

        assertEquals(1L, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("hash123", user.getPasswordHash());
        assertEquals("alice@test.com", user.getEmail());
        assertEquals("http://img.com/avatar.png", user.getAvatarUrl());
        assertEquals("Hello!", user.getStatusMessage());
        assertTrue(user.isOnline());
        assertEquals(now, user.getLastSeen());
        assertEquals(now, user.getCreatedAt());
    }

    @Test
    void testSettersAndGetters() {
        User user = new User();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        user.setId(42L);
        user.setUsername("bob");
        user.setPasswordHash("hashed_pw");
        user.setEmail("bob@example.com");
        user.setAvatarUrl("/avatars/bob.png");
        user.setStatusMessage("Busy");
        user.setOnline(true);
        user.setLastSeen(now);
        user.setCreatedAt(now);

        assertEquals(42L, user.getId());
        assertEquals("bob", user.getUsername());
        assertEquals("hashed_pw", user.getPasswordHash());
        assertEquals("bob@example.com", user.getEmail());
        assertEquals("/avatars/bob.png", user.getAvatarUrl());
        assertEquals("Busy", user.getStatusMessage());
        assertTrue(user.isOnline());
        assertEquals(now, user.getLastSeen());
        assertEquals(now, user.getCreatedAt());
    }

    @Test
    void testSetOnlineToggle() {
        User user = new User();
        assertFalse(user.isOnline());
        user.setOnline(true);
        assertTrue(user.isOnline());
        user.setOnline(false);
        assertFalse(user.isOnline());
    }

    @Test
    void testNullableFields() {
        User user = new User();
        user.setEmail(null);
        user.setAvatarUrl(null);
        user.setStatusMessage(null);
        user.setLastSeen(null);
        assertNull(user.getEmail());
        assertNull(user.getAvatarUrl());
        assertNull(user.getStatusMessage());
        assertNull(user.getLastSeen());
    }
}
