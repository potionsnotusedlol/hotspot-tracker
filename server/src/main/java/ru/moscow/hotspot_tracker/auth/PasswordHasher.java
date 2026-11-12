package ru.moscow.hotspot_tracker.auth;

/**
 * Контракт хэширования паролей (FR-AUTH-01).
 * Алгоритм — Argon2id, параметры задаются конфигурацией.
 */
public interface PasswordHasher {

    /**
     * Возвращает хэш пароля для сохранения в app_user.password_hash.
     */
    String hash(String rawPassword);

    /**
     * Проверяет пароль против сохранённого хэша.
     */
    boolean matches(String rawPassword, String passwordHash);
}
