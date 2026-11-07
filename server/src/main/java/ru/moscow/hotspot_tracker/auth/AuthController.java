package ru.moscow.hotspot_tracker.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.moscow.hotspot_tracker.auth.dto.AuthResponse;
import ru.moscow.hotspot_tracker.auth.dto.LoginRequest;
import ru.moscow.hotspot_tracker.auth.dto.RegisterRequest;

/**
 * HTTP-контракт регистрации и входа (FR-AUTH-01).
 * Базовый путь /api/v1/auth. Реализация появится после согласования.
 */
@RequestMapping("/api/v1/auth")
public interface AuthController {

    /**
     * Регистрирует пользователя и возвращает 201 с профилем.
     */
    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest body);

    /**
     * Проверяет учётные данные и возвращает 200 с профилем.
     */
    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@RequestBody LoginRequest body);
}
