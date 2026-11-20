package ru.moscow.hotspot_tracker.auth;

import ru.moscow.hotspot_tracker.auth.dto.AuthResponse;
import ru.moscow.hotspot_tracker.auth.dto.LoginRequest;
import ru.moscow.hotspot_tracker.auth.dto.RegisterRequest;

/**
 * Контракт регистрации и входа (FR-AUTH-01).
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
}
