package ru.moscow.hotspot_tracker.auth;

import ru.moscow.hotspot_tracker.user.Role;

import java.util.UUID;

/**
 * Authenticated principal restored from an access token (FR-AUTH-02).
 */
public record AuthenticatedUser(UUID id, String email, Role role, long tokenVersion) {
}
