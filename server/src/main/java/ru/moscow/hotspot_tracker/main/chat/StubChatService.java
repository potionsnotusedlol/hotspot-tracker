package ru.moscow.hotspot_tracker.main.chat;

import org.springframework.stereotype.Service;

/** ВРЕМЕННАЯ ЗАГЛУШКА: модель ещё не подключена. Удалить с появлением ChatService (№7). */
@Service
class StubChatService implements ChatService {

    @Override
    public String reply(String userEmail, String message) {
        return "Чат-бот пока не подключён. Воспользуйтесь каталогом и статистикой.";
    }
}
