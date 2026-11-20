package ru.moscow.hotspot_tracker.auth;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * RSA keys for signing and verifying access tokens (FR-AUTH-02).
 */
public interface JwtKeyProvider {

    /**
     * Returns the private key used to sign access tokens.
     */
    PrivateKey privateKey();

    /**
     * Returns the public key used to verify access tokens.
     */
    PublicKey publicKey();
}
