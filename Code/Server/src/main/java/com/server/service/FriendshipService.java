package com.server.service;

import com.google.gson.JsonArray;
import com.server.repository.FriendshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service layer for friendship operations.
 * Wraps FriendshipRepository with business-logic validation.
 */
public class FriendshipService {
    private static final Logger logger = LoggerFactory.getLogger(FriendshipService.class);
    private final FriendshipRepository repo = new FriendshipRepository();

    public String sendFriendRequest(long senderId, long receiverId) {
        if (senderId == receiverId) return "self";
        return repo.sendFriendRequest(senderId, receiverId);
    }

    public boolean respondToRequest(long responderId, long requesterId, String decision) {
        return repo.respondToRequest(responderId, requesterId, decision);
    }

    public boolean cancelFriendRequest(long senderId, long receiverId) {
        return repo.cancelFriendRequest(senderId, receiverId);
    }

    public boolean unfriend(long userId, long otherId) {
        return repo.unfriend(userId, otherId);
    }

    public String getFriendshipStatus(long viewerId, long otherId) {
        return repo.getFriendshipStatus(viewerId, otherId);
    }

    public JsonArray getFriendList(long userId) {
        return repo.getFriendList(userId);
    }

    public JsonArray getPendingRequests(long userId) {
        return repo.getPendingRequests(userId);
    }

    public JsonArray getSentRequests(long userId) {
        return repo.getSentRequests(userId);
    }

    public int countPendingRequests(long userId) {
        return repo.countPendingRequests(userId);
    }
}
