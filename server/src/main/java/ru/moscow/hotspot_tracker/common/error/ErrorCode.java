package ru.moscow.hotspot_tracker.common.error;

/**
 * Коды ошибок в поле code ответа problem+json (FR-AUTH-01).
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_BODY,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    USER_DISABLED,
    UNAUTHORIZED,
    INTERNAL_ERROR
}
