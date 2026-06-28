package com.client.model;

/**
 * Simple user data model used on the client side.
 */
public class User {
    private long userId;
    private String username;
    private String email;
    private String avatarUrl;
    private boolean online;
    private String lastSeen;

    public User() {}

    public User(long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
}
