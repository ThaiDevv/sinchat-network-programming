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
}
