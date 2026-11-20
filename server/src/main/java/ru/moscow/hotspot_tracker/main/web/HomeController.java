package ru.moscow.hotspot_tracker.main.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.OkrugCount;

/** Главная со сводкой (FR-WEB-02): всего точек и распределение по округам. */
@Controller
class HomeController {

    private final StatsService stats;

    HomeController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/")
    String home(Model model) {
        var okrugs = stats.byOkrug();
        model.addAttribute("okrugs", okrugs);
        model.addAttribute("total", okrugs.stream().mapToLong(OkrugCount::count).sum());
        return "pages/home";
    }
}
