package ru.moscow.hotspot_tracker.auth.dto;

import ru.moscow.hotspot_tracker.user.Role;

import java.util.UUID;

/**
 * Результат успешной регистрации или входа (FR-AUTH-01).
 * Выдача access и refresh-токенов добавляется в FR-AUTH-02.
 */
public record AuthResponse(UUID id, String email, Role role) {
}
