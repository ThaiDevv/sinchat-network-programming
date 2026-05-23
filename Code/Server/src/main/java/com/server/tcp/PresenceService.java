package com.server.tcp;

import com.google.gson.JsonObject;
import com.server.repository.UserRepository;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PresenceService {
    private static final PresenceService INSTANCE = new PresenceService();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository = new UserRepository();

    private PresenceService() {}

    public static PresenceService getInstance() {
        return INSTANCE;
    }

    public void onUserOnline(long userId) {
        userRepository.updateOnlineStatusWithoutLastSeen(userId, true);
        broadcastStatusToFriends(userId, "online", null);
    }

    public void onUserOffline(long userId) {
        userRepository.updateOnlineStatus(userId, false);
        Timestamp lastSeen = userRepository.findLastSeen(userId);
        broadcastStatusToFriends(userId, "offline", lastSeen);
    }

    private void broadcastStatusToFriends(long userId, String status, Timestamp lastSeen) {
        List<Long> friendIds = userRepository.findAcceptedFriendIds(userId);
        if (friendIds.isEmpty()) {
            return;
        }

        JsonObject event = new JsonObject();
        event.addProperty("action", "USER_STATUS_EVENT");
        event.addProperty("userId", userId);
        event.addProperty("status", status);
        if (lastSeen != null) {
            event.addProperty("lastSeen", TS_FMT.format(lastSeen.toLocalDateTime()));
        }

        TcpConnectionManager manager = TcpConnectionManager.getInstance();
        for (Long friendId : friendIds) {
            manager.broadcastToUser(friendId, event);
        }
    }
}
