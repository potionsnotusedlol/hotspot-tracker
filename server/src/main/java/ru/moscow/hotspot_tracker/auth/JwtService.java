package ru.moscow.hotspot_tracker.auth;

/**
 * Access token issuing and validation (FR-AUTH-02).
 */
public interface JwtService {

    /**
     * Signs an RS256 access token valid for 15 minutes.
     */
    String issueAccessToken(AuthenticatedUser user);

    /**
     * Verifies signature, expiry, issuer, audience and token type.
     */
    AuthenticatedUser decode(String accessToken);

    /**
     * Checks that the token version matches the current value stored for the user.
     */
    boolean verifyTokenVersion(AuthenticatedUser user);
}
