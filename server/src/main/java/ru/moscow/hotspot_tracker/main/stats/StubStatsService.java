package ru.moscow.hotspot_tracker.main.stats;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.accesspoint.AccessPointService.CatalogFilter;
import ru.moscow.hotspot_tracker.main.accesspoint.TransportType;

/**
 * ВРЕМЕННАЯ ЗАГЛУШКА. Считает статистику по демонстрационным точкам из заглушки каталога.
 * Удалить, когда модуль данных (№2) подключит настоящую статистику.
 */
@Service
class StubStatsService implements StatsService {

    private static final CatalogFilter NO_FILTER =
            new CatalogFilter(null, null, null, null, null, null, null, null, null);

    private final AccessPointService accessPoints;

    StubStatsService(AccessPointService accessPoints) {
        this.accessPoints = accessPoints;
    }

    @Override
    public List<OkrugCount> byOkrug() {
        return all().stream()
                .collect(Collectors.groupingBy(AccessPointView::okrug, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new OkrugCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public List<DistrictCount> byDistrict(String okrug) {
        return all().stream()
                .filter(p -> p.okrug().equals(okrug))
                .collect(Collectors.groupingBy(AccessPointView::district, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new DistrictCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public List<DistanceBucket> byStopDistance(TransportType transportType) {
        List<AccessPointView> points = all().stream()
                .filter(p -> transportType == null || p.nearestTransport() == transportType)
                .toList();
        return List.of(
                new DistanceBucket("0–100 м", count(points, 0, 100)),
                new DistanceBucket("100–250 м", count(points, 100, 250)),
                new DistanceBucket("250–500 м", count(points, 250, 500)),
                new DistanceBucket("500–1000 м", count(points, 500, 1000)),
                new DistanceBucket("свыше 1000 м", count(points, 1000, Integer.MAX_VALUE)));
    }

    @Override
    public List<AccessPointView> topByLoad(int limit) {
        return all().stream()
                .sorted(Comparator.comparingDouble(AccessPointView::estimatedLoad).reversed())
                .limit(limit)
                .toList();
    }

    private List<AccessPointView> all() {
        return accessPoints.search(NO_FILTER, PageRequest.of(0, 1000)).getContent();
    }

    private static long count(List<AccessPointView> points, int from, int to) {
        return points.stream().filter(p -> p.distanceToStopM() >= from && p.distanceToStopM() < to).count();
    }
}
