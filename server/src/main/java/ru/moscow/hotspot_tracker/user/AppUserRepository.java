package ru.moscow.hotspot_tracker.user;

import java.util.Optional;

/**
 * Контракт доступа к таблице app_user (FR-AUTH-01).
 */
public interface AppUserRepository {

    /**
     * Ищет пользователя по нормализованному email.
     */
    Optional<AppUser> findByEmailNormalized(String emailNormalized);

    /**
     * Проверяет, занят ли email при регистрации.
     */
    boolean existsByEmailNormalized(String emailNormalized);

    /**
     * Сохраняет нового пользователя.
     */
    AppUser save(AppUser user);
}
