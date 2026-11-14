package ru.moscow.hotspot_tracker.common.error;

import java.net.URI;
import java.util.Map;

/**
 * Тело ошибки в формате RFC 9457 (application/problem+json).
 */
public record ProblemDetails(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        ErrorCode code,
        Map<String, String> errors
) {
}
