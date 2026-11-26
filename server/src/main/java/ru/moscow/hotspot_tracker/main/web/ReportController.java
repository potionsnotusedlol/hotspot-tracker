package ru.moscow.hotspot_tracker.main.web;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import ru.moscow.hotspot_tracker.main.report.ReportJob;
import ru.moscow.hotspot_tracker.main.report.ReportParams;
import ru.moscow.hotspot_tracker.main.report.ReportService;
import ru.moscow.hotspot_tracker.main.report.ReportStatus;
import ru.moscow.hotspot_tracker.main.report.ReportType;

/** Страница отчётов (FR-REP-01…06): создание, статус и скачивание Excel. */
@Controller
class ReportController {

    private static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports")
    String page(Authentication auth, Model model) {
        boolean admin = isAdmin(auth);
        model.addAttribute("reportTypes", Arrays.stream(ReportType.values())
                .filter(t -> admin || !t.adminOnly())
                .toList());
        model.addAttribute("rows", rows(auth));
        model.addAttribute("hasActive", hasActive(auth));
        return "reports/index";
    }

    /** Форма отправляется через HTMX: в ответ приходит только список. Без JavaScript — обычный redirect. */
    @PostMapping("/reports")
    String create(@RequestParam ReportType type,
                  @RequestParam(required = false) Long accessPointId,
                  @RequestParam(required = false) String okrug,
                  @RequestParam(required = false) String district,
                  @RequestParam(required = false) Integer days,
                  @RequestHeader(name = "HX-Request", required = false) String htmxRequest,
                  Authentication auth,
                  Model model,
                  RedirectAttributes redirect) {
        ReportParams params = new ReportParams(accessPointId, okrug, district, days);
        try {
            reports.request(type, params, auth.getName(), isAdmin(auth));
            if (htmxRequest == null) {
                redirect.addFlashAttribute("message", "Отчёт поставлен в очередь. Статус обновится сам.");
                return "redirect:/reports";
            }
        } catch (IllegalArgumentException | AccessDeniedException e) {
            if (htmxRequest == null) {
                redirect.addFlashAttribute("error", e.getMessage());
                return "redirect:/reports";
            }
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("rows", rows(auth));
        model.addAttribute("hasActive", hasActive(auth));
        return "reports/list :: list";
    }

    /** Список для опроса статуса. Опрашивается, пока есть отчёты в очереди или в работе. */
    @GetMapping("/reports/list")
    String list(Authentication auth, Model model) {
        model.addAttribute("rows", rows(auth));
        model.addAttribute("hasActive", hasActive(auth));
        return "reports/list :: list";
    }

    @GetMapping("/reports/{id}/file")
    ResponseEntity<Resource> download(@PathVariable UUID id, Authentication auth) {
        try {
            Path file = reports.readyFile(id, auth.getName(), isAdmin(auth));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wifi-report-" + id + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new FileSystemResource(file));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Отчёт не найден или ещё не готов");
        }
    }

    private List<ReportRow> rows(Authentication auth) {
        return reports.listFor(auth.getName()).stream().map(ReportRow::of).toList();
    }

    private boolean hasActive(Authentication auth) {
        return reports.listFor(auth.getName()).stream()
                .anyMatch(j -> j.getStatus() == ReportStatus.PENDING || j.getStatus() == ReportStatus.RUNNING);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Строка таблицы: всё уже подготовлено для шаблона. */
    record ReportRow(UUID id, String type, String status, String created, boolean ready, boolean failed,
                     String error) {

        static ReportRow of(ReportJob job) {
            return new ReportRow(
                    job.getId(),
                    job.getReportType().label(),
                    job.getStatus().label(),
                    CREATED_FORMAT.format(job.getCreatedAt()),
                    job.getStatus() == ReportStatus.DONE,
                    job.getStatus() == ReportStatus.FAILED,
                    job.getErrorMessage());
        }
    }
}
