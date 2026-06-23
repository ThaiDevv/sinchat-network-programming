package com.server.model;


public class Friendship {
    // Enum  `status` ENUM('PENDING','ACCEPTED','BLOCKED')
    public enum FriendshipStatus { PENDING, ACCEPTED, BLOCKED }

    private long user1Id;               // bigint NOT NULL - FK den users.id
    private long user2Id;               // bigint NOT NULL - FK den users.id
    private FriendshipStatus status;    // trang thai ket ban
    private long actionUserId;          // ai la nguoi gui loi moi (khi PENDING) hoac block (khi BLOCKED)

    public Friendship() {}

    public Friendship(long user1Id, long user2Id, FriendshipStatus status, long actionUserId) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.status = status;
        this.actionUserId = actionUserId;
    }

    public long getUser1Id() { return user1Id; }
    public void setUser1Id(long user1Id) { this.user1Id = user1Id; }

    public long getUser2Id() { return user2Id; }
    public void setUser2Id(long user2Id) { this.user2Id = user2Id; }

    public FriendshipStatus getStatus() { return status; }
    public void setStatus(FriendshipStatus status) { this.status = status; }

    public long getActionUserId() { return actionUserId; }
    public void setActionUserId(long actionUserId) { this.actionUserId = actionUserId; }
}
