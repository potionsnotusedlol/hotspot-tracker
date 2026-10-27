package com.example.bot;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {

    public static void main(String[] args) throws Exception {
        // Токен берём из переменной окружения, чтобы не хранить его в коде
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Ошибка: не задана переменная окружения BOT_TOKEN.");
            System.err.println("Получите токен у @BotFather и задайте его, см. README.md");
            System.exit(1);
        }

        TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
        try {
            application.registerBot(token, new MyBot(token));
        } catch (TelegramApiException e) {
            System.err.println("Не удалось запустить бота. Причина: " + e);
            System.err.println("Ошибка 401 значит неверный токен. Проверьте BOT_TOKEN и подключение к интернету.");
            application.close();
            System.exit(1);
        }

        // Корректно закрываем соединение при остановке (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                application.close();
            } catch (Exception ignored) {
            }
        }));

        System.out.println("Бот запущен. Для остановки нажмите Ctrl+C.");
        Thread.currentThread().join();
    }
}
