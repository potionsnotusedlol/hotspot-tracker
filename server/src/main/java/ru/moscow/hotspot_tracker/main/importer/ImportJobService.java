package ru.moscow.hotspot_tracker.main.importer;

import java.time.Instant;
import java.util.List;

/** Журнал импорта CSV (FR-ADM-03, таблица import_job). Владелец — модуль данных (№2). */
public interface ImportJobService {

    /** Последние запуски импорта, новые сверху. */
    List<ImportJob> recent(int limit);

    record ImportJob(long id, String dataset, String status, int accepted, int rejected,
                     String author, Instant startedAt) {}
}
