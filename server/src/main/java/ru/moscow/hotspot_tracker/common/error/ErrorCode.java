package ru.moscow.hotspot_tracker.common.error;

/**
 * Коды ошибок в поле code ответа problem+json.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_BODY,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    USER_DISABLED,
    UNAUTHORIZED,
    INVALID_TOKEN,
    TOKEN_EXPIRED,
    INVALID_REFRESH_TOKEN,
    REFRESH_TOKEN_REUSED,
    INTERNAL_ERROR
}
