package com.server.model;


public class Friendship {
    // Enum  `status` ENUM('PENDING','ACCEPTED','BLOCKED')
    public enum FriendshipStatus { PENDING, ACCEPTED, BLOCKED }

    private long user1Id;               // bigint NOT NULL — FK → users.id
    private long user2Id;               // bigint NOT NULL — FK → users.id
    private FriendshipStatus status;    // trạng thái kết bạn

    public Friendship() {}

    public Friendship(long user1Id, long user2Id, FriendshipStatus status) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.status = status;
    }

    public long getUser1Id() { return user1Id; }
    public void setUser1Id(long user1Id) { this.user1Id = user1Id; }

    public long getUser2Id() { return user2Id; }
    public void setUser2Id(long user2Id) { this.user2Id = user2Id; }

    public FriendshipStatus getStatus() { return status; }
    public void setStatus(FriendshipStatus status) { this.status = status; }
}
