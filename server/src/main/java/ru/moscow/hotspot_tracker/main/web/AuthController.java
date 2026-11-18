package ru.moscow.hotspot_tracker.main.web;

import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService;

/**
 * Вход и регистрация (FR-AUTH-01). Сам вход обрабатывает Spring Security по POST /login,
 * здесь только страницы и регистрация.
 */
@Controller
class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 128;
    private static final int MAX_EMAIL = 255;

    private final UserAccountService accounts;

    AuthController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    String registerForm() {
        return "auth/register";
    }

    @PostMapping("/register")
    String register(@RequestParam String email,
                    @RequestParam String password,
                    @RequestParam String passwordRepeat,
                    RedirectAttributes redirect,
                    Model model) {
        String normalized = email == null ? "" : email.trim();
        String error = validate(normalized, password, passwordRepeat);
        if (error == null) {
            try {
                accounts.register(normalized, password);
                redirect.addFlashAttribute("message", "Аккаунт создан. Войдите с этим email и паролем.");
                return "redirect:/login";
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        model.addAttribute("error", error);
        model.addAttribute("email", normalized);
        return "auth/register";
    }

    /** Текст ошибки для пользователя, или null, если всё верно. Пароль в ответ не возвращаем. */
    private static String validate(String email, String password, String passwordRepeat) {
        if (email.isEmpty() || email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            return "Введите корректный email";
        }
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            return "Пароль должен быть от " + MIN_PASSWORD + " до " + MAX_PASSWORD + " символов";
        }
        if (!password.equals(passwordRepeat)) {
            return "Пароли не совпадают";
        }
        return null;
    }
}
