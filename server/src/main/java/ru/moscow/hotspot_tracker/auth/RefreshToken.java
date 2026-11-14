package ru.moscow.hotspot_tracker.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token record (FR-AUTH-02).
 * Only the SHA-256 hash of the token value is stored in the database.
 */
public class RefreshToken {

    private UUID id;
    private UUID userId;
    private String tokenHash;
    private UUID familyId;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    private UUID replacedBy;
}
