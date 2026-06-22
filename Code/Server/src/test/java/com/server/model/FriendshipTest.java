package com.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendshipTest {

    @Test
    void testDefaultConstructor() {
        Friendship f = new Friendship();
        assertEquals(0, f.getUser1Id());
        assertEquals(0, f.getUser2Id());
        assertNull(f.getStatus());
    }

    @Test
    void testParameterizedConstructor() {
        Friendship f = new Friendship(1L, 2L, Friendship.FriendshipStatus.ACCEPTED, 1L);
        assertEquals(1L, f.getUser1Id());
        assertEquals(2L, f.getUser2Id());
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, f.getStatus());
    }

    @Test
    void testSettersAndGetters() {
        Friendship f = new Friendship();
        f.setUser1Id(10L);
        f.setUser2Id(20L);
        f.setStatus(Friendship.FriendshipStatus.PENDING);

        assertEquals(10L, f.getUser1Id());
        assertEquals(20L, f.getUser2Id());
        assertEquals(Friendship.FriendshipStatus.PENDING, f.getStatus());
    }

    @Test
    void testFriendshipStatusEnum() {
        assertEquals(3, Friendship.FriendshipStatus.values().length);
        assertEquals(Friendship.FriendshipStatus.PENDING, Friendship.FriendshipStatus.valueOf("PENDING"));
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, Friendship.FriendshipStatus.valueOf("ACCEPTED"));
        assertEquals(Friendship.FriendshipStatus.BLOCKED, Friendship.FriendshipStatus.valueOf("BLOCKED"));
    }

    @Test
    void testStatusTransitions() {
        Friendship f = new Friendship(1L, 2L, Friendship.FriendshipStatus.PENDING, 1L);
        assertEquals(Friendship.FriendshipStatus.PENDING, f.getStatus());

        f.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, f.getStatus());

        f.setStatus(Friendship.FriendshipStatus.BLOCKED);
        assertEquals(Friendship.FriendshipStatus.BLOCKED, f.getStatus());
    }
}
