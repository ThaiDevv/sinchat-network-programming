package com.client.model;

/**
 * Response returned by ChatService after each TCP request.
 */
public record ApiResponse(
        int statusCode,
        String status,
        String message,
        String code,
        Long userId,
        String rawBody
) {
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300 && !"error".equalsIgnoreCase(status);
    }
}
