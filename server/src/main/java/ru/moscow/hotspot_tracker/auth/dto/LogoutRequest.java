package ru.moscow.hotspot_tracker.auth.dto;

/**
 * Body of POST /api/v1/auth/logout (FR-AUTH-02).
 */
public record LogoutRequest(String refreshToken) {
}
