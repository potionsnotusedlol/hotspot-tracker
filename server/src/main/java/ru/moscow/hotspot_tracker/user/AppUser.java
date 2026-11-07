package ru.moscow.hotspot_tracker.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Учётная запись. Контракт данных таблицы app_user (FR-AUTH-01), без поведения.
 * Пароль хранится только как хэш Argon2id.
 */
public class AppUser {

    private UUID id;
    private String email;
    private String emailNormalized;
    private String passwordHash;
    private Role role;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
