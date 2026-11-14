package ru.moscow.hotspot_tracker.auth.dto;

/**
 * Тело запроса регистрации (FR-AUTH-01).
 * email — корректный адрес до 254 символов, password — от 12 до 128 символов.
 */
public record RegisterRequest(String email, String password) {
}
