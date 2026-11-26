package ru.moscow.hotspot_tracker.main.web.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** ЗАГЛУШКА веба: модель ещё не подключена, отвечаем фиксированным текстом. */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderChatService implements ChatService {

    @Override
    public String reply(String userEmail, String message) {
        return "Чат-бот пока не подключён. Воспользуйтесь каталогом и статистикой.";
    }
}
