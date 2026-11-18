package ru.moscow.hotspot_tracker.main.accesspoint;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Каталог точек доступа (FR-CAT). Владелец интерфейса — тимлид, реализация — модуль данных.
 * Веб-контроллеры вызывают этот сервис напрямую и не дублируют фильтры или расчёты.
 */
public interface AccessPointService {

    /** Страница точек по фильтрам (FR-CAT-01, FR-CAT-02). */
    Page<AccessPointView> search(CatalogFilter filter, Pageable pageable);

    /** Карточка точки или пусто, если точки нет. */
    Optional<AccessPointView> findById(long id);

    /** Ближайшие остановки по видам транспорта (FR-CAT-04). */
    List<NearestStop> nearestStops(long id);

    /** Почасовая расчётная нагрузка для дня недели (FR-LOAD-01). */
    List<HourlyLoad> loadProfile(long id, DayType dayType);

    /** Точки для карты в пределах фильтра, не больше limit (FR-WEB-03). */
    List<MapPoint> mapPoints(CatalogFilter filter, int limit);

    record AccessPointView(
            long id,
            String name,
            String address,
            String okrug,
            String district,
            TransportType nearestTransport,
            int distanceToStopM,
            double estimatedLoad,
            LoadLevel loadLevel,
            double latitude,
            double longitude) {}

    /** Фильтры каталога (FR-CAT-02). Пустые поля не участвуют в фильтрации. */
    record CatalogFilter(
            String okrug,
            String district,
            String query,
            TransportType transportType,
            Integer distanceFrom,
            Integer distanceTo,
            Double loadFrom,
            Double loadTo,
            String status) {}

    record NearestStop(TransportType transportType, String name, int distanceM, int rank) {}

    record HourlyLoad(int hour, double estimatedLoad) {}

    record MapPoint(long id, String name, double latitude, double longitude,
                    double estimatedLoad, LoadLevel loadLevel) {}
}
