package ru.moscow.hotspot_tracker.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.moscow.hotspot_tracker.auth.dto.AuthResponse;
import ru.moscow.hotspot_tracker.auth.dto.LoginRequest;
import ru.moscow.hotspot_tracker.auth.dto.LogoutRequest;
import ru.moscow.hotspot_tracker.auth.dto.RefreshRequest;
import ru.moscow.hotspot_tracker.auth.dto.RegisterRequest;
import ru.moscow.hotspot_tracker.auth.dto.TokenResponse;

/**
 * HTTP-контракт регистрации и входа (FR-AUTH-01).
 * Ручки refresh и logout добавлены в FR-AUTH-02.
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

    /**
     * Rotates the refresh token and returns a new token pair.
     * Errors: 401 invalid_refresh_token, 401 refresh_token_reused.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest body);

    /**
     * Revokes the refresh token family and returns 204.
     * Repeating the call is not an error.
     */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody LogoutRequest body);
}
