package com.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChangeAvatarTest {

    @Test
    void testDefaultConstructor() {
        ChangeAvatar ca = new ChangeAvatar();
        assertEquals(0, ca.getUserId());
        assertNull(ca.getAvatarUrl());
    }

    @Test
    void testParameterizedConstructor() {
        ChangeAvatar ca = new ChangeAvatar(1L, "/avatars/user1.png");
        assertEquals(1L, ca.getUserId());
        assertEquals("/avatars/user1.png", ca.getAvatarUrl());
    }

    @Test
    void testSettersAndGetters() {
        ChangeAvatar ca = new ChangeAvatar();
        ca.setUserId(42L);
        ca.setAvatarUrl("http://cdn.example.com/avatar.jpg");

        assertEquals(42L, ca.getUserId());
        assertEquals("http://cdn.example.com/avatar.jpg", ca.getAvatarUrl());
    }

    @Test
    void testNullAvatarUrl() {
        ChangeAvatar ca = new ChangeAvatar(1L, null);
        assertNull(ca.getAvatarUrl());
    }
}
