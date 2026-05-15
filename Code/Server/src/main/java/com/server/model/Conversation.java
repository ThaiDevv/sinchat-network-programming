package com.server.model;

import java.sql.Timestamp;

public class Conversation {
    // Enum ánh xạ cột `type` ENUM('PRIVATE','GROUP')
    public enum ConversationType { PRIVATE, GROUP }

    private long id;                        // bigint(20) NOT NULL AUTO_INCREMENT
    private ConversationType type;          // enum NOT NULL
    private String name;                    // varchar(100) — tên nhóm (null nếu PRIVATE)
    private String avatarUrl;               // text — ảnh đại diện nhóm
    private Long createdBy;                 // bigint — ID người tạo (FK → users.id)
    private Timestamp createdAt;            // timestamp DEFAULT CURRENT_TIMESTAMP
    private Timestamp lastMessageAt;        // timestamp — thời điểm tin nhắn cuối

    public Conversation() {}

    public Conversation(long id, ConversationType type, String name, String avatarUrl,
                        Long createdBy, Timestamp createdAt, Timestamp lastMessageAt) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public ConversationType getType() { return type; }
    public void setType(ConversationType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Timestamp lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
