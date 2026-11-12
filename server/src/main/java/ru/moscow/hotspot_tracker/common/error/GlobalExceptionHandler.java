package ru.moscow.hotspot_tracker.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Контракт преобразования исключений в problem+json.
 */
public interface GlobalExceptionHandler {

    /**
     * Преобразует доменную ошибку в problem+json с нужным статусом и кодом.
     */
    ResponseEntity<ProblemDetails> handleApi(ApiException ex, HttpServletRequest request);

    /**
     * Отдаёт 400 malformed_body при нечитаемом теле запроса.
     */
    ResponseEntity<ProblemDetails> handleMalformed(HttpMessageNotReadableException ex,
                                                   HttpServletRequest request);

    /**
     * Отдаёт 500 internal_error, не раскрывая внутренние детали.
     */
    ResponseEntity<ProblemDetails> handleUnexpected(Exception ex, HttpServletRequest request);
}
