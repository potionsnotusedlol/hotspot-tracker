package ru.moscow.hotspot_tracker.auth.dto;

/**
 * Token pair returned by refresh (FR-AUTH-02).
 * Lifetime values are in seconds.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn
) {
}
