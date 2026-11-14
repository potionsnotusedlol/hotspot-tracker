package ru.moscow.hotspot_tracker.auth.dto;

/**
 * Body of POST /api/v1/auth/refresh (FR-AUTH-02).
 */
public record RefreshRequest(String refreshToken) {
}
