package ru.moscow.hotspot_tracker.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for refresh tokens (FR-AUTH-02).
 */
public interface RefreshTokenRepository {

    /**
     * Reads a token by hash and locks the row for the rotation transaction.
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /**
     * Reads a token by hash without locking.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Stores a newly issued refresh token.
     */
    RefreshToken save(RefreshToken token);

    /**
     * Revokes every token of the family and returns the number of updated rows.
     */
    int revokeFamily(UUID familyId, Instant revokedAt);

    /**
     * Revokes every token of the user and returns the number of updated rows.
     */
    int revokeAllByUser(UUID userId, Instant revokedAt);

    /**
     * Deletes tokens whose expiry is before the given instant.
     */
    int deleteExpired(Instant now);
}
