package ru.moscow.hotspot_tracker.auth;

import ru.moscow.hotspot_tracker.auth.dto.AuthResponse;
import ru.moscow.hotspot_tracker.auth.dto.LoginRequest;
import ru.moscow.hotspot_tracker.auth.dto.LogoutRequest;
import ru.moscow.hotspot_tracker.auth.dto.RefreshRequest;
import ru.moscow.hotspot_tracker.auth.dto.RegisterRequest;
import ru.moscow.hotspot_tracker.auth.dto.TokenResponse;

/**
 * Контракт регистрации и входа (FR-AUTH-01).
 * Ротация и отзыв refresh-токенов добавлены в FR-AUTH-02.
 */
public interface AuthService {

    /**
     * Проверяет, что email свободен, хэширует пароль Argon2id,
     * сохраняет пользователя с ролью USER и возвращает профиль.
     * Ошибки: 409 email_taken, 400 validation_failed.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Проверяет email и пароль, возвращает профиль пользователя.
     * Ошибки: 401 invalid_credentials, 403 user_disabled.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Rotates the refresh token and issues a new access token.
     * Errors: 401 invalid_refresh_token, 401 refresh_token_reused.
     */
    TokenResponse refresh(RefreshRequest request);

    /**
     * Revokes the refresh token family of the presented token.
     */
    void logout(LogoutRequest request);
}
