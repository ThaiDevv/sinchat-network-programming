package com.server.tcp;

import com.google.gson.JsonObject;
import com.server.repository.ConversationRepository;
import com.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PresenceService {
    private static final Logger logger = LoggerFactory.getLogger(PresenceService.class);
    private static final PresenceService INSTANCE = new PresenceService();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository = new UserRepository();
    private final ConversationRepository conversationRepository = new ConversationRepository();

    private PresenceService() {}

    public static PresenceService getInstance() {
        return INSTANCE;
    }

    public void onUserOnline(long userId) {
        logger.info("[PRESENCE ONLINE] UserId={} - Updating online status and broadcasting to friends and conversation peers", userId);
        userRepository.updateOnlineStatusWithoutLastSeen(userId, true);
        broadcastStatusToPeers(userId, "online", null);
    }

    public void onUserOffline(long userId) {
        logger.info("[PRESENCE OFFLINE] UserId={} - Updating offline status and broadcasting to friends and conversation peers", userId);
        userRepository.updateOnlineStatus(userId, false);
        Timestamp lastSeen = userRepository.findLastSeen(userId);
        broadcastStatusToPeers(userId, "offline", lastSeen);
    }

    /**
     * Broadcast status change to:
     * 1. All accepted friends
     * 2. All users who share a conversation with this user (conversation peers)
     */
    private void broadcastStatusToPeers(long userId, String status, Timestamp lastSeen) {
        // Collect all target user IDs
        Set<Long> targetIds = new HashSet<>();

        // 1. Friends
        List<Long> friendIds = userRepository.findAcceptedFriendIds(userId);
        if (friendIds != null) {
            targetIds.addAll(friendIds);
        }

        // 2. Conversation peers (users sharing a conversation, excluding self)
        List<Long> conversationPeers = conversationRepository.findConversationPeers(userId);
        if (conversationPeers != null) {
            targetIds.addAll(conversationPeers);
        }

        if (targetIds.isEmpty()) {
            logger.info("[PRESENCE BROADCAST] UserId={} | Status={} - No peers to broadcast to", userId, status);
            return;
        }

        logger.info("[PRESENCE BROADCAST] UserId={} | Status={} | TargetCount={} | Friends={} | ConversationPeers={} | lastSeen={}",
                userId, status, targetIds.size(),
                friendIds != null ? friendIds.size() : 0,
                conversationPeers != null ? conversationPeers.size() : 0,
                lastSeen);

        JsonObject event = new JsonObject();
        event.addProperty("action", "USER_STATUS_EVENT");
        event.addProperty("userId", userId);
        event.addProperty("status", status);
        if (lastSeen != null) {
            event.addProperty("lastSeen", TS_FMT.format(lastSeen.toLocalDateTime()));
        }

        TcpConnectionManager manager = TcpConnectionManager.getInstance();
        for (Long targetId : targetIds) {
            logger.info("[PRESENCE BROADCAST] UserId={} | Status={} -> TargetId={}", userId, status, targetId);
            manager.broadcastToUser(targetId, event);
        }
    }

    /**
     * Broadcast avatar change to:
     * 1. All accepted friends
     * 2. All users who share a conversation with this user
     */
    public void broadcastAvatarChangeToPeers(long userId, String avatarUrl) {
        Set<Long> targetIds = new HashSet<>();

        List<Long> friendIds = userRepository.findAcceptedFriendIds(userId);
        if (friendIds != null) {
            targetIds.addAll(friendIds);
        }

        List<Long> conversationPeers = conversationRepository.findConversationPeers(userId);
        if (conversationPeers != null) {
            targetIds.addAll(conversationPeers);
        }

        if (targetIds.isEmpty()) return;

        logger.info("[PRESENCE BROADCAST] UserId={} | AvatarChanged | TargetCount={}", userId, targetIds.size());

        JsonObject event = new JsonObject();
        event.addProperty("action", "USER_AVATAR_CHANGED_EVENT");
        event.addProperty("userId", userId);
        event.addProperty("avatarUrl", avatarUrl);

        TcpConnectionManager manager = TcpConnectionManager.getInstance();
        for (Long targetId : targetIds) {
            manager.broadcastToUser(targetId, event);
        }
    }
}
