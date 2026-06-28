package com.client.model;

/**
 * Message data model used on the client side.
 */
public class Message {
    private long id;
    private long conversationId;
    private long senderId;
    private String senderUsername;
    private String content;
    private String status;
    private String createdAt;
    private Long replyToId;
    private String replyToUsername;
    private String replyToContent;
    private Long forwardFromId;
    private String forwardFromUsername;
    private String forwardFromContent;
    private boolean pinned;
    private Long pinnedBy;

    public Message() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }

    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

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

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public Long getPinnedBy() { return pinnedBy; }
    public void setPinnedBy(Long pinnedBy) { this.pinnedBy = pinnedBy; }
}
