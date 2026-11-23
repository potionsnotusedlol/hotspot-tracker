package ru.moscow.hotspot_tracker.auth;

/**
 * Generates opaque refresh token values (FR-AUTH-02).
 */
public interface TokenGenerator {

    /**
     * Returns a new 256-bit random token encoded as base64url.
     */
    String generate();

    /**
     * Returns the SHA-256 hash of the raw token, used for storage and lookup.
     */
    String hash(String rawToken);
}
