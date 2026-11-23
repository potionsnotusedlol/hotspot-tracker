package ru.moscow.hotspot_tracker.auth.dto;

/**
 * Тело запроса входа (FR-AUTH-01).
 */
public record LoginRequest(String email, String password) {
}
