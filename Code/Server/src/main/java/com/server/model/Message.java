package com.server.model;

import java.sql.Timestamp;

public class Message {
    // Enum map voi cot type trong database.
    public enum MessageType { TEXT, IMAGE, VIDEO, VOICE, FILE, SYSTEM }

    private long id;                    // bigint(20) NOT NULL AUTO_INCREMENT
    private long conversationId;        // bigint(20) NOT NULL - FK den conversations.id
    private long senderId;              // bigint(20) NOT NULL - FK den users.id
    private MessageType type;           // enum DEFAULT 'TEXT'
    private String content;             // text - noi dung tin nhan
    private Timestamp createdAt;        // timestamp DEFAULT CURRENT_TIMESTAMP
    private String status;              // Trạng thái tin nhắn (SENT, DELIVERED, SEEN) - join từ bảng message_status
    private String senderUsername;
    private Long replyToId;
    private String replyToUsername;
    private String replyToContent;
    private Long forwardFromId;
    private String forwardFromUsername;
    private String forwardFromContent;

    public Message() {}

    public Message(long id, long conversationId, long senderId, MessageType type,
                   String content, Timestamp createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }

    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public Long getReplyToId() { return replyToId; }
    public void setReplyToId(Long replyToId) { this.replyToId = replyToId; }

    public String getReplyToUsername() { return replyToUsername; }
    public void setReplyToUsername(String replyToUsername) { this.replyToUsername = replyToUsername; }

    public String getReplyToContent() { return replyToContent; }
    public void setReplyToContent(String replyToContent) { this.replyToContent = replyToContent; }

    public Long getForwardFromId() { return forwardFromId; }
    public void setForwardFromId(Long forwardFromId) { this.forwardFromId = forwardFromId; }

    public String getForwardFromUsername() { return forwardFromUsername; }
    public void setForwardFromUsername(String forwardFromUsername) { this.forwardFromUsername = forwardFromUsername; }

    public String getForwardFromContent() { return forwardFromContent; }
    public void setForwardFromContent(String forwardFromContent) { this.forwardFromContent = forwardFromContent; }

    public static class SeenUserInfo {
        private long userId;
        private String username;

        public SeenUserInfo(long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public long getUserId() { return userId; }
        public String getUsername() { return username; }
    }

    private java.util.List<SeenUserInfo> seenByUsers;
    public java.util.List<SeenUserInfo> getSeenByUsers() { return seenByUsers; }
    public void setSeenByUsers(java.util.List<SeenUserInfo> seenByUsers) { this.seenByUsers = seenByUsers; }
}

