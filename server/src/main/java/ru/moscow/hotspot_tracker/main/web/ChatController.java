package ru.moscow.hotspot_tracker.main.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import ru.moscow.hotspot_tracker.main.web.port.ChatService;

/** Виджет чата (FR-WEB-05): вопрос уходит в ChatService, в ответ приходит кусок HTML для HTMX. */
@Controller
class ChatController {

    private static final int MAX_LENGTH = 500;

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/chat/widget")
    String ask(@RequestParam(required = false) String message, Authentication auth, Model model) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сообщение пустое или длиннее 500 символов");
        }
        model.addAttribute("question", text);
        model.addAttribute("answer", chat.reply(auth.getName(), text));
        return "chat/messages :: exchange";
    }
}
