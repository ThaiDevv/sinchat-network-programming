package com.server.service;

import com.google.gson.JsonArray;
import com.server.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FriendshipServiceTest {

    private FriendshipService friendshipService;
    private FriendshipRepository mockRepo;

    @BeforeEach
    void setUp() throws Exception {
        friendshipService = new FriendshipService();
        mockRepo = mock(FriendshipRepository.class);

        Field repoField = FriendshipService.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        repoField.set(friendshipService, mockRepo);
    }

    @Test
    void testSendFriendRequestSuccess() {
        when(mockRepo.sendFriendRequest(1L, 2L)).thenReturn("sent");
        String result = friendshipService.sendFriendRequest(1L, 2L);
        assertEquals("sent", result);
    }

    @Test
    void testSendFriendRequestToSelf() {
        String result = friendshipService.sendFriendRequest(5L, 5L);
        assertEquals("self", result);
        verify(mockRepo, never()).sendFriendRequest(anyLong(), anyLong());
    }

    @Test
    void testSendFriendRequestAlreadyFriends() {
        when(mockRepo.sendFriendRequest(1L, 2L)).thenReturn("already_friends");
        String result = friendshipService.sendFriendRequest(1L, 2L);
        assertEquals("already_friends", result);
    }

    @Test
    void testRespondToRequestAccept() {
        when(mockRepo.respondToRequest(2L, 1L, "ACCEPTED")).thenReturn(true);
        boolean ok = friendshipService.respondToRequest(2L, 1L, "ACCEPTED");
        assertTrue(ok);
    }

    @Test
    void testRespondToRequestReject() {
        when(mockRepo.respondToRequest(2L, 1L, "REJECTED")).thenReturn(true);
        boolean ok = friendshipService.respondToRequest(2L, 1L, "REJECTED");
        assertTrue(ok);
    }

    @Test
    void testRespondToRequestNotFound() {
        when(mockRepo.respondToRequest(2L, 999L, "ACCEPTED")).thenReturn(false);
        boolean ok = friendshipService.respondToRequest(2L, 999L, "ACCEPTED");
        assertFalse(ok);
    }

    @Test
    void testCancelFriendRequest() {
        when(mockRepo.cancelFriendRequest(1L, 2L)).thenReturn(true);
        boolean ok = friendshipService.cancelFriendRequest(1L, 2L);
        assertTrue(ok);
    }

    @Test
    void testCancelFriendRequestNotFound() {
        when(mockRepo.cancelFriendRequest(1L, 2L)).thenReturn(false);
        boolean ok = friendshipService.cancelFriendRequest(1L, 2L);
        assertFalse(ok);
    }

    @Test
    void testUnfriend() {
        when(mockRepo.unfriend(1L, 2L)).thenReturn(true);
        boolean ok = friendshipService.unfriend(1L, 2L);
        assertTrue(ok);
    }

    @Test
    void testUnfriendNotFound() {
        when(mockRepo.unfriend(1L, 2L)).thenReturn(false);
        boolean ok = friendshipService.unfriend(1L, 2L);
        assertFalse(ok);
    }

    @Test
    void testBlockUser() {
        when(mockRepo.blockUser(1L, 2L)).thenReturn(true);
        boolean ok = friendshipService.blockUser(1L, 2L);
        assertTrue(ok);
    }

    @Test
    void testBlockSelf() {
        boolean ok = friendshipService.blockUser(5L, 5L);
        assertFalse(ok);
        verify(mockRepo, never()).blockUser(anyLong(), anyLong());
    }

    @Test
    void testUnblockUser() {
        when(mockRepo.unblockUser(1L, 2L)).thenReturn(true);
        boolean ok = friendshipService.unblockUser(1L, 2L);
        assertTrue(ok);
    }

    @Test
    void testUnblockSelf() {
        boolean ok = friendshipService.unblockUser(5L, 5L);
        assertFalse(ok);
        verify(mockRepo, never()).unblockUser(anyLong(), anyLong());
    }

    @Test
    void testGetFriendshipStatus() {
        when(mockRepo.getFriendshipStatus(1L, 2L)).thenReturn("ACCEPTED");
        String status = friendshipService.getFriendshipStatus(1L, 2L);
        assertEquals("ACCEPTED", status);
    }

    @Test
    void testGetFriendshipStatusNone() {
        when(mockRepo.getFriendshipStatus(1L, 2L)).thenReturn("NONE");
        String status = friendshipService.getFriendshipStatus(1L, 2L);
        assertEquals("NONE", status);
    }

    @Test
    void testGetFriendList() {
        JsonArray expected = new JsonArray();
        when(mockRepo.getFriendList(1L)).thenReturn(expected);
        JsonArray result = friendshipService.getFriendList(1L);
        assertSame(expected, result);
    }

    @Test
    void testGetPendingRequests() {
        JsonArray expected = new JsonArray();
        when(mockRepo.getPendingRequests(1L)).thenReturn(expected);
        JsonArray result = friendshipService.getPendingRequests(1L);
        assertSame(expected, result);
    }

    @Test
    void testGetSentRequests() {
        JsonArray expected = new JsonArray();
        when(mockRepo.getSentRequests(1L)).thenReturn(expected);
        JsonArray result = friendshipService.getSentRequests(1L);
        assertSame(expected, result);
    }

    @Test
    void testCountPendingRequests() {
        when(mockRepo.countPendingRequests(1L)).thenReturn(3);
        int count = friendshipService.countPendingRequests(1L);
        assertEquals(3, count);
    }
}
