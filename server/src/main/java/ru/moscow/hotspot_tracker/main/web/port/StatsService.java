package ru.moscow.hotspot_tracker.main.web.port;

import java.util.List;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;

/**
 * Статистика по точкам доступа (FR-STAT). Владелец — модуль данных (№2), веб показывает результат как есть.
 */
public interface StatsService {

    /** Количество точек по административным округам (FR-STAT-01). */
    List<OkrugCount> byOkrug();

    /** Количество точек по районам выбранного округа (FR-STAT-01). */
    List<DistrictCount> byDistrict(String okrug);

    /**
     * Распределение по расстоянию до ближайшей остановки (FR-STAT-02, FR-STAT-03).
     * transportType = null означает все виды транспорта.
     */
    List<DistanceBucket> byStopDistance(TransportType transportType);

    /** Топ точек по расчётной нагрузке (FR-STAT-04). */
    List<AccessPointView> topByLoad(int limit);

    record OkrugCount(String okrug, long count) {}

    record DistrictCount(String district, long count) {}

    record DistanceBucket(String label, long count) {}
}
