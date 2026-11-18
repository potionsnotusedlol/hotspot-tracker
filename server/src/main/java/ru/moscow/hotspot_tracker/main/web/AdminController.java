package ru.moscow.hotspot_tracker.main.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.moscow.hotspot_tracker.main.web.port.AuditLogService;
import ru.moscow.hotspot_tracker.main.web.port.AuditLogService.AuditEntry;
import ru.moscow.hotspot_tracker.main.web.port.ImportJobService;
import ru.moscow.hotspot_tracker.main.web.port.ModelParamService;
import ru.moscow.hotspot_tracker.main.web.port.ModelParamService.ModelParam;
import ru.moscow.hotspot_tracker.main.web.port.Role;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService.UserAccount;
import ru.moscow.hotspot_tracker.main.web.port.UsageEventService;

/**
 * Админ-панель (FR-ADM-01…03): пользователи и роли, параметры модели, журналы, сводка использования.
 * Доступ закрыт правилом в SecurityConfig: только роль ADMIN.
 */
@Controller
@RequestMapping("/admin")
class AdminController {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private static final int USAGE_DAYS = 30;
    private static final int MAX_PARAM_LENGTH = 100;

    private final UserAccountService users;
    private final ModelParamService params;
    private final ImportJobService imports;
    private final AuditLogService audit;
    private final UsageEventService usage;

    AdminController(UserAccountService users, ModelParamService params, ImportJobService imports,
                    AuditLogService audit, UsageEventService usage) {
        this.users = users;
        this.params = params;
        this.imports = imports;
        this.audit = audit;
        this.usage = usage;
    }

    @GetMapping
    String dashboard(Model model) {
        List<UserAccount> accounts = users.findAll();
        model.addAttribute("users", accounts);
        model.addAttribute("roles", Role.values());
        model.addAttribute("params", params.findAll().stream().map(ParamRow::of).toList());
        model.addAttribute("imports", imports.recent(20));
        model.addAttribute("audit", audit.recent(50).stream().map(AuditRow::of).toList());

        Instant to = Instant.now();
        model.addAttribute("usage", usage.summary(to.minus(Duration.ofDays(USAGE_DAYS)), to));
        model.addAttribute("usageDays", USAGE_DAYS);
        return "admin/index";
    }

    @PostMapping("/users/{id}/role")
    String changeRole(@PathVariable long id, @RequestParam Role role,
                      Authentication auth, RedirectAttributes redirect) {
        try {
            // Админ не может снять роль с себя: иначе легко потерять доступ к панели
            if (role != Role.ADMIN && isSelf(id, auth)) {
                throw new IllegalArgumentException("Нельзя снять роль администратора с самого себя");
            }
            users.changeRole(id, role, auth.getName());
            redirect.addFlashAttribute("message", "Роль изменена");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/model-params")
    String updateParam(@RequestParam String key, @RequestParam String value,
                       Authentication auth, RedirectAttributes redirect) {
        String trimmed = value == null ? "" : value.trim();
        try {
            if (trimmed.isEmpty() || trimmed.length() > MAX_PARAM_LENGTH) {
                throw new IllegalArgumentException("Значение пустое или длиннее " + MAX_PARAM_LENGTH + " символов");
            }
            params.update(key, trimmed, auth.getName());
            redirect.addFlashAttribute("message", "Параметр «" + key + "» сохранён");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    private boolean isSelf(long id, Authentication auth) {
        return users.findAll().stream().anyMatch(u -> u.id() == id && u.email().equals(auth.getName()));
    }

    record ParamRow(String key, String value, int version, String changedBy, String changedAt) {

        static ParamRow of(ModelParam p) {
            return new ParamRow(p.key(), p.value(), p.version(), p.changedBy(), FORMAT.format(p.changedAt()));
        }
    }

    record AuditRow(String at, String actor, String action, String target) {

        static AuditRow of(AuditEntry e) {
            return new AuditRow(FORMAT.format(e.at()), e.actor(), e.action(), e.target());
        }
    }
}
