package ru.moscow.hotspot_tracker.common.error;

/**
 * Доменная ошибка с HTTP-статусом и кодом для problem+json.
 */
public class ApiException extends RuntimeException {

    private int status;
    private ErrorCode code;
    private String detail;
}
