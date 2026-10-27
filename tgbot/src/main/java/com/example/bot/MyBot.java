package com.example.bot;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class MyBot implements LongPollingSingleThreadUpdateConsumer {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss (VV)");

    private final TelegramClient client;

    public MyBot(String token) {
        this.client = new OkHttpTelegramClient(token);
    }

    /** Вызывается для каждого нового события от Telegram. */
    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                onMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ---------- Обычные сообщения и команды ----------

    private void onMessage(Message message) throws TelegramApiException {
        long chatId = message.getChatId();
        String text = message.getText().trim();

        // В группах команды приходят в виде /start@my_bot — убираем "@my_bot"
        String command = text.split("\\s+")[0].split("@")[0].toLowerCase();

        switch (command) {
            case "/start" -> {
                String name = message.getFrom() != null ? message.getFrom().getFirstName() : "друг";
                send(chatId, "Привет, " + name + "! 👋\nЯ простой бот на Java.\nНапиши /help, чтобы увидеть команды.");
            }
            case "/help" -> send(chatId, """
                    Доступные команды:
                    /start — приветствие
                    /help — эта справка
                    /time — текущее время
                    /roll — случайное число от 1 до 100
                    /menu — меню с кнопками

                    Любой другой текст я просто повторю.""");
            case "/time" -> send(chatId, "🕒 " + ZonedDateTime.now().format(TIME_FORMAT));
            case "/roll" -> send(chatId, "🎲 Выпало: " + ThreadLocalRandom.current().nextInt(1, 101));
            case "/menu" -> sendMenu(chatId);
            default -> send(chatId, "Ты написал: " + text);
        }
    }

    // ---------- Нажатия на inline-кнопки ----------

    private void onCallback(CallbackQuery query) throws TelegramApiException {
        long chatId = query.getMessage().getChatId();
        int messageId = query.getMessage().getMessageId();

        String answer = switch (query.getData()) {
            case "time" -> "🕒 " + ZonedDateTime.now().format(TIME_FORMAT);
            case "roll" -> "🎲 Выпало: " + ThreadLocalRandom.current().nextInt(1, 101);
            case "about" -> "Я учебный бот на Java + библиотека TelegramBots.";
            default -> "Неизвестная кнопка";
        };

        // Убираем "часики" на кнопке
        client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());

        // Заменяем текст исходного сообщения, кнопки оставляем
        client.execute(EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(answer + "\n\nВыбери ещё что-нибудь:")
                .replyMarkup(menuKeyboard())
                .build());
    }

    // ---------- Вспомогательные методы ----------

    private void sendMenu(long chatId) throws TelegramApiException {
        client.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Выбери действие:")
                .replyMarkup(menuKeyboard())
                .build());
    }

    private InlineKeyboardMarkup menuKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("🕒 Время", "time"),
                        button("🎲 Число", "roll")))
                .keyboardRow(new InlineKeyboardRow(
                        button("ℹ️ О боте", "about")))
                .build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private void send(long chatId, String text) throws TelegramApiException {
        client.execute(SendMessage.builder().chatId(chatId).text(text).build());
    }
}
