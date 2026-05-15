package com.server.model;

import java.sql.Timestamp;

public class User {
    private long id;                  // bigint(20) NOT NULL AUTO_INCREMENT
    private String username;          // varchar(50) NOT NULL UNIQUE
    private String passwordHash;      // varchar(255) NOT NULL
    private String email;             // varchar(100) UNIQUE
    private String avatarUrl;         // text — URL ảnh đại diện
    private String statusMessage;     // varchar(255) — dòng trạng thái
    private boolean isOnline;         // tinyint(1) DEFAULT 0
    private Timestamp lastSeen;       // timestamp — lần hoạt động cuối
    private Timestamp createdAt;      // timestamp DEFAULT CURRENT_TIMESTAMP

    public User() {}

    public User(long id, String username, String passwordHash, String email,
                String avatarUrl, String statusMessage, boolean isOnline,
                Timestamp lastSeen, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.statusMessage = statusMessage;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
