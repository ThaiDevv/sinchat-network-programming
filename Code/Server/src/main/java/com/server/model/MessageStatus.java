package com.server.model;

import java.sql.Timestamp;

public class MessageStatus {
    // Enum ánh xạ cột `status` ENUM('SENT','DELIVERED','SEEN')
    public enum Status { SENT, DELIVERED, SEEN }

    private long messageId;     // bigint — FK → messages.id
    private long userId;        // bigint — FK → users.id (người nhận)
    private Status status;      // trạng thái tin nhắn
    private Timestamp updatedAt;// timestamp ON UPDATE CURRENT_TIMESTAMP

    public MessageStatus() {}

    public MessageStatus(long messageId, long userId, Status status, Timestamp updatedAt) {
        this.messageId = messageId;
        this.userId = userId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
