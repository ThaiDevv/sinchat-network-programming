package com.client.model;

/**
 * Conversation data model used on the client side.
 */
public class Conversation {
    private long conversationId;
    private String displayName;
    private String lastMessage;
    private long lastMessageSenderId;
    private long peerId;
    private boolean isOnline;
    private String lastSeen;

    public Conversation() {}

    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageSenderId() { return lastMessageSenderId; }
    public void setLastMessageSenderId(long lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

    public long getPeerId() { return peerId; }
    public void setPeerId(long peerId) { this.peerId = peerId; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
}
