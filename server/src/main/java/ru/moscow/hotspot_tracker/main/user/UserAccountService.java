package ru.moscow.hotspot_tracker.main.user;

import java.util.List;
import java.util.Optional;

/**
 * Учётные записи (FR-AUTH). Владелец — модуль безопасности (№3). Веб использует его для входа,
 * регистрации и управления ролями.
 */
public interface UserAccountService {

    List<UserAccount> findAll();

    /** Меняет роль и пишет в журнал аудита, кто это сделал. */
    void changeRole(long id, Role role, String actor);

    /** Регистрация по email и паролю; пароль сохраняется только хэшем (FR-AUTH-01). */
    UserAccount register(String email, String rawPassword);

    /** Данные для входа по email. Пароль в виде хэша. */
    Optional<Credentials> findCredentials(String email);

    record UserAccount(long id, String email, Role role) {}

    record Credentials(String email, String passwordHash, Role role) {}
}
