package com.server.model;

public class ChangeAvatar {

    private long userId;
    private String avatarUrl;

    public ChangeAvatar() {
    }

    public ChangeAvatar(long userId, String avatarUrl) {
        this.userId = userId;
        this.avatarUrl = avatarUrl;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

}
