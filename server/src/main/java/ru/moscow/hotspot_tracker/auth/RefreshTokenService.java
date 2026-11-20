package ru.moscow.hotspot_tracker.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Rotation and revocation of refresh tokens (FR-AUTH-02).
 */
public interface RefreshTokenService {

    /**
     * Creates a new token family and the first refresh token for the user.
     */
    String issue(UUID userId);

    /**
     * Validates the presented token, marks it as used and returns a new token
     * in the same family. Reuse of a used token revokes the whole family.
     */
    String rotate(String rawRefreshToken);

    /**
     * Revokes the whole family of the presented token.
     */
    void revokeFamily(String rawRefreshToken);

    /**
     * Revokes every family of the user.
     */
    void revokeAllForUser(UUID userId);

    /**
     * Deletes expired tokens.
     */
    void deleteExpired(Instant now);
}
