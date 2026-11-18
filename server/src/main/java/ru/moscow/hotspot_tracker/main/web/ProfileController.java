package ru.moscow.hotspot_tracker.main.web;

import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Профиль: кто вошёл и с какими ролями. Привязка Telegram появится вместе с ботом. */
@Controller
class ProfileController {

    @GetMapping("/profile")
    String profile(Authentication auth, Model model) {
        model.addAttribute("email", auth.getName());
        model.addAttribute("roles", auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.joining(", ")));
        return "profile/index";
    }
}
