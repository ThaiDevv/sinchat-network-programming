package com.server.model;

import java.sql.Timestamp;

public class MessageSearchResult {
    private long id;
    private long conversationId;
    private long senderId;
    private String senderUsername;
    private Message.MessageType type;
    private String content;
    private Timestamp createdAt;

    public MessageSearchResult(
            long id,
            long conversationId,
            long senderId,
            String senderUsername,
            Message.MessageType type,
            String content,
            Timestamp createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public long getConversationId() { return conversationId; }
    public long getSenderId() { return senderId; }
    public String getSenderUsername() { return senderUsername; }
    public Message.MessageType getType() { return type; }
    public String getContent() { return content; }
    public Timestamp getCreatedAt() { return createdAt; }
}
