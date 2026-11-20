package ru.moscow.hotspot_tracker.main.web;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistanceBucket;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.DistrictCount;
import ru.moscow.hotspot_tracker.main.web.port.StatsService.OkrugCount;

/** Статистика по округам, районам и расстояниям до транспорта (FR-STAT-01…04). */
@Controller
class StatsController {

    private static final int TOP_LIMIT = 10;

    private final StatsService stats;

    StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    String stats(@RequestParam(required = false) String okrug,
                 @RequestParam(required = false) TransportType transportType,
                 Model model) {
        String selectedOkrug = StringUtils.hasText(okrug) ? okrug : null;

        List<OkrugCount> okrugs = stats.byOkrug();
        model.addAttribute("okrugs", okrugs);
        model.addAttribute("okrugLabels", okrugs.stream().map(OkrugCount::okrug).collect(Collectors.joining("|")));
        model.addAttribute("okrugValues", okrugs.stream().map(o -> String.valueOf(o.count())).collect(Collectors.joining(",")));
        model.addAttribute("total", okrugs.stream().mapToLong(OkrugCount::count).sum());

        model.addAttribute("selectedOkrug", selectedOkrug);
        List<DistrictCount> districts = selectedOkrug == null ? List.of() : stats.byDistrict(selectedOkrug);
        model.addAttribute("districts", districts);
        model.addAttribute("districtLabels", districts.stream().map(DistrictCount::district).collect(Collectors.joining("|")));
        model.addAttribute("districtValues", districts.stream().map(d -> String.valueOf(d.count())).collect(Collectors.joining(",")));

        List<DistanceBucket> buckets = stats.byStopDistance(transportType);
        model.addAttribute("transportTypes", TransportType.values());
        model.addAttribute("selectedTransport", transportType);
        model.addAttribute("buckets", buckets);
        model.addAttribute("bucketLabels", buckets.stream().map(DistanceBucket::label).collect(Collectors.joining("|")));
        model.addAttribute("bucketValues", buckets.stream().map(b -> String.valueOf(b.count())).collect(Collectors.joining(",")));

        model.addAttribute("top", stats.topByLoad(TOP_LIMIT));
        return "stats/index";
    }
}
