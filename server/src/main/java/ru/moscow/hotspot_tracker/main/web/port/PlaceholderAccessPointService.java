package ru.moscow.hotspot_tracker.main.web.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * ЗАГЛУШКА веба: демонстрационные точки, пока модуль данных не реализовал AccessPointService.
 * Отключается свойством hotspot.web.placeholders=false, когда появляется настоящая реализация.
 */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderAccessPointService implements AccessPointService {

    private static final String[][] DISTRICTS = {
            {"Центральный", "Тверской"},
            {"Северный", "Савёловский"},
            {"Южный", "Нагатинский"},
            {"Западный", "Очаково-Матвеевское"}
    };

    private static final List<AccessPointView> POINTS = buildSample();

    @Override
    public Page<AccessPointView> search(CatalogFilter filter, Pageable pageable) {
        List<AccessPointView> matched = POINTS.stream().filter(matches(filter)).toList();
        int from = (int) Math.min(pageable.getOffset(), matched.size());
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        return new PageImpl<>(matched.subList(from, to), pageable, matched.size());
    }

    @Override
    public Optional<AccessPointView> findById(long id) {
        return POINTS.stream().filter(p -> p.id() == id).findFirst();
    }

    @Override
    public List<NearestStop> nearestStops(long id) {
        return List.of(
                new NearestStop(TransportType.BUS, "Остановка «Улица Демо», 1", 120, 1),
                new NearestStop(TransportType.METRO, "Станция «Демо»", 480, 1));
    }

    @Override
    public List<HourlyLoad> loadProfile(long id, DayType dayType) {
        double base = findById(id).map(AccessPointView::estimatedLoad).orElse(100.0);
        double factor = dayType == DayType.WEEKEND ? 0.8 : 1.0;
        List<HourlyLoad> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            // Условный суточный ход: пик вечером, минимум ночью
            double wave = 0.6 + 0.4 * Math.sin((hour - 6) * Math.PI / 12);
            hours.add(new HourlyLoad(hour, Math.round(base * factor * wave)));
        }
        return hours;
    }

    @Override
    public List<MapPoint> mapPoints(CatalogFilter filter, int limit) {
        return POINTS.stream()
                .filter(matches(filter))
                .limit(limit)
                .map(p -> new MapPoint(p.id(), p.name(), p.latitude(), p.longitude(),
                        p.estimatedLoad(), p.loadLevel()))
                .toList();
    }

    private static Predicate<AccessPointView> matches(CatalogFilter filter) {
        return p -> (filter.okrug() == null || filter.okrug().equals(p.okrug()))
                && (filter.district() == null || filter.district().equals(p.district()))
                && (filter.query() == null || p.name().toLowerCase().contains(filter.query().toLowerCase()))
                && (filter.transportType() == null || filter.transportType() == p.nearestTransport())
                && (filter.distanceFrom() == null || p.distanceToStopM() >= filter.distanceFrom())
                && (filter.distanceTo() == null || p.distanceToStopM() <= filter.distanceTo())
                && (filter.loadFrom() == null || p.estimatedLoad() >= filter.loadFrom())
                && (filter.loadTo() == null || p.estimatedLoad() <= filter.loadTo());
    }

    private static List<AccessPointView> buildSample() {
        TransportType[] types = TransportType.values();
        List<AccessPointView> points = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            String[] district = DISTRICTS[i % DISTRICTS.length];
            double load = 40 + (i * 37) % 260;
            points.add(new AccessPointView(
                    i,
                    "Точка " + i,
                    "Адрес демо, " + i,
                    district[0] + " округ",
                    district[1],
                    types[i % types.length],
                    50 + (i * 73) % 1200,
                    load,
                    load < 100 ? LoadLevel.LOW : load < 200 ? LoadLevel.MEDIUM : LoadLevel.HIGH,
                    55.70 + (i % 15) * 0.01,
                    37.45 + (i % 20) * 0.02));
        }
        return List.copyOf(points);
    }
}
