package ru.moscow.hotspot_tracker.main.user;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ВРЕМЕННАЯ ЗАГЛУШКА. Учётные записи в памяти процесса, после перезапуска пропадают.
 * Удалить, когда модуль безопасности (№3) подключит хранение в app_user.
 * Администратора здесь не создаём: роль ADMIN выдаётся только через реальный модуль.
 */
@Service
class StubUserAccountService implements UserAccountService {

    private final Map<String, Stored> byEmail = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public List<UserAccount> findAll() {
        return byEmail.values().stream()
                .map(s -> new UserAccount(s.id(), s.email(), s.role()))
                .sorted(java.util.Comparator.comparingLong(UserAccount::id))
                .toList();
    }

    @Override
    public synchronized void changeRole(long id, Role role, String actor) {
        Stored stored = byEmail.values().stream()
                .filter(s -> s.id() == id)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Пользователь не найден: " + id));
        byEmail.put(stored.email(), new Stored(stored.id(), stored.email(), stored.passwordHash(), role));
    }

    @Override
    public synchronized UserAccount register(String email, String rawPassword) {
        if (byEmail.containsKey(email)) {
            throw new IllegalArgumentException("Такой email уже зарегистрирован");
        }
        Stored stored = new Stored(ids.incrementAndGet(), email, encoder.encode(rawPassword), Role.USER);
        byEmail.put(email, stored);
        return new UserAccount(stored.id(), stored.email(), stored.role());
    }

    @Override
    public Optional<Credentials> findCredentials(String email) {
        return Optional.ofNullable(byEmail.get(email))
                .map(s -> new Credentials(s.email(), s.passwordHash(), s.role()));
    }

    private record Stored(long id, String email, String passwordHash, Role role) {}
}
