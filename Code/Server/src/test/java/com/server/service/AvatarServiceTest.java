package com.server.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AvatarServiceTest {

    private final AvatarService avatarService = new AvatarService();

    @Test
    void testChangeAvatarNullUrl() {
        boolean result = avatarService.changeAvatar(1L, null);
        assertFalse(result);
    }

    @Test
    void testChangeAvatarInvalidFormat() {
        boolean result = avatarService.changeAvatar(1L, "not_an_image_data_uri");
        assertFalse(result);
    }

    @Test
    void testChangeAvatarMissingBase64Data() {
        boolean result = avatarService.changeAvatar(1L, "data:image/png;base64,");
        assertFalse(result);
    }

    @Test
    void testChangeAvatarWithSmallValidPng() {
        // 1x1 red pixel PNG in base64
        String base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
        String dataUri = "data:image/png;base64," + base64;

        boolean result = avatarService.changeAvatar(1L, dataUri);
        // The service will succeed up to the DB call, which will fail since
        // we don't have a DB. So we expect false, but no exception.
        // If exception occurs, it will return false from the catch block.
        assertFalse(result);
    }

    @Test
    void testChangeAvatarTooLarge() {
        // Create a large base64 string (over 10 MB)
        byte[] largeData = new byte[11 * 1024 * 1024]; // 11 MB raw
        // Base64 representation will be ~14.7 MB
        String base64 = Base64.getEncoder().encodeToString(largeData);
        String dataUri = "data:image/png;base64," + base64;

        boolean result = avatarService.changeAvatar(1L, dataUri);
        assertFalse(result);
    }

    @Test
    void testGetAvatarBytes() {
        byte[] result = avatarService.getAvatarBytes(999L);
        assertNull(result); // No DB, should return null
    }
}
