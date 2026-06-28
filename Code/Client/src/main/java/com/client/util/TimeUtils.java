package com.client.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Time formatting utilities for presence / last-seen display.
 * Extracted from ChatView.
 */
public final class TimeUtils {
    private TimeUtils() {}

    private static final DateTimeFormatter LAST_SEEN_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LAST_SEEN_FMT_NANOS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");

    /**
     * Converts a lastSeen string into a Vietnamese relative time string.
     * Examples: "Vừa mới hoạt động", "Hoạt động 5 phút trước", "Hoạt động 2 giờ trước", "Offline"
     */
    public static String formatRelativePresence(String lastSeenStr) {
        try {
            LocalDateTime lastSeenTime;
            try {
                lastSeenTime = LocalDateTime.parse(lastSeenStr, LAST_SEEN_FMT);
            } catch (Exception e1) {
                lastSeenTime = LocalDateTime.parse(lastSeenStr.trim(), LAST_SEEN_FMT_NANOS);
            }
            LocalDateTime now = LocalDateTime.now();
            long minutes = ChronoUnit.MINUTES.between(lastSeenTime, now);

            if (minutes < 0) minutes = 0;

            if (minutes < 1) {
                return "Vừa mới hoạt động";
            } else if (minutes < 60) {
                return "Hoạt động " + minutes + " phút trước";
            } else {
                long hours = minutes / 60;
                if (hours >= 24) {
                    return "Offline";
                }
                return "Hoạt động " + hours + " giờ trước";
            }
        } catch (Exception e) {
            return "Offline";
        }
    }
}
