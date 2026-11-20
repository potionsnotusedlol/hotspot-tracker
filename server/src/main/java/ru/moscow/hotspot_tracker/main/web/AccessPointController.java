package ru.moscow.hotspot_tracker.main.web;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.HourlyLoad;
import ru.moscow.hotspot_tracker.main.web.port.DayType;

/** Карточка точки (FR-CAT-04): данные, карта, ближайшие остановки и почасовая нагрузка. */
@Controller
class AccessPointController {

    private final AccessPointService accessPoints;

    AccessPointController(AccessPointService accessPoints) {
        this.accessPoints = accessPoints;
    }

    @GetMapping("/access-points/{id}")
    String card(@PathVariable long id, Model model) {
        AccessPointView point = accessPoints.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Точка не найдена"));

        List<HourlyLoad> weekday = accessPoints.loadProfile(id, DayType.WEEKDAY);
        List<HourlyLoad> weekend = accessPoints.loadProfile(id, DayType.WEEKEND);

        model.addAttribute("point", point);
        model.addAttribute("stops", accessPoints.nearestStops(id).stream()
                .sorted(Comparator.comparing(stop -> stop.transportType().label()))
                .toList());
        model.addAttribute("hourLabels", hourLabels());
        model.addAttribute("weekdayValues", values(weekday));
        model.addAttribute("weekendValues", values(weekend));
        model.addAttribute("weekdayPeak", peak(weekday));
        model.addAttribute("weekendPeak", peak(weekend));
        return "access-points/card";
    }

    /** Подписи часов: «0:00|1:00|…|23:00». Разделитель «|», потому что подписи содержат двоеточие. */
    private static String hourLabels() {
        return IntStream.range(0, 24).mapToObj(h -> h + ":00").reduce((a, b) -> a + "|" + b).orElse("");
    }

    private static String values(List<HourlyLoad> hours) {
        return hours.stream().map(h -> String.valueOf(Math.round(h.estimatedLoad())))
                .reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String peak(List<HourlyLoad> hours) {
        return hours.stream()
                .max(Comparator.comparingDouble(HourlyLoad::estimatedLoad))
                .map(h -> h.hour() + ":00, около " + Math.round(h.estimatedLoad()) + " чел. (оценка)")
                .orElse("нет данных");
    }
}
