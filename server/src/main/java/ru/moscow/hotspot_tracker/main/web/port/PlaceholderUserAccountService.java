package ru.moscow.hotspot_tracker.main.web.port;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ЗАГЛУШКА веба: учётных записей пока нет. Вход всегда неуспешен, регистрация и роли недоступны.
 * Отключается с модулем безопасности.
 */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderUserAccountService implements UserAccountService {

    @Override
    public List<UserAccount> findAll() {
        return List.of();
    }

    @Override
    public void changeRole(long id, Role role, String actor) {
        throw new IllegalArgumentException("Управление ролями появится с модулем безопасности");
    }

    @Override
    public UserAccount register(String email, String rawPassword) {
        throw new IllegalArgumentException("Регистрация пока недоступна");
    }

    @Override
    public Optional<Credentials> findCredentials(String email) {
        return Optional.empty();
    }
}
