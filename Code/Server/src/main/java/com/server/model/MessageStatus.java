package com.server.model;

import java.sql.Timestamp;

public class MessageStatus {
    // Enum map voi cot status trong database.
    public enum Status { SENT, DELIVERED, SEEN }

    private long messageId;     // bigint - FK den messages.id
    private long userId;        // bigint - FK den users.id (nguoi nhan)
    private Status status;      // trang thai tin nhan
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
