package ru.moscow.hotspot_tracker.main.web;

import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.MapPoint;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;

/** Карта точек с кластерами, окрашенными по расчётной нагрузке (FR-WEB-03). */
@Controller
class MapController {

    /** Больше точек на карте отдавать не будем: тысячи маркеров тормозят браузер. */
    private static final int MAX_POINTS = 2000;

    private final AccessPointService accessPoints;
    private final StatsService stats;

    MapController(AccessPointService accessPoints, StatsService stats) {
        this.accessPoints = accessPoints;
        this.stats = stats;
    }

    @GetMapping("/map")
    String map(@RequestParam(required = false) String okrug, Model model) {
        String selected = StringUtils.hasText(okrug) ? okrug : null;
        String source = UriComponentsBuilder.fromPath("/map/points")
                .queryParamIfPresent("okrug", Optional.ofNullable(selected))
                .build().encode().toUriString();

        model.addAttribute("okrugs", stats.byOkrug());
        model.addAttribute("selectedOkrug", selected);
        model.addAttribute("source", source);
        return "map/index";
    }

    /** Точки для карты в формате JSON. Открыт всем, как и сама карта. */
    @GetMapping(value = "/map/points", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    List<MapPoint> points(@RequestParam(required = false) String okrug) {
        String selected = StringUtils.hasText(okrug) ? okrug : null;
        return accessPoints.mapPoints(
                new CatalogFilter(selected, null, null, null, null, null, null, null, null),
                MAX_POINTS);
    }
}
