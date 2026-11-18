package ru.moscow.hotspot_tracker.main.web.port;

/** Чат-бот на Spring AI (FR-CHAT). Владелец — №7. Веб передаёт сообщение и показывает ответ. */
public interface ChatService {

    String reply(String userEmail, String message);
}
