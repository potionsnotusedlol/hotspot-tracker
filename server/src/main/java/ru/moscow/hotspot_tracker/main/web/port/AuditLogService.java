package ru.moscow.hotspot_tracker.main.web.port;

import java.time.Instant;
import java.util.List;

/** Журнал аудита: вход, смена ролей, импорт, отчёты, изменение параметров (FR-AUTH-08, FR-ADM-03). */
public interface AuditLogService {

    /** Последние записи, новые сверху. */
    List<AuditEntry> recent(int limit);

    record AuditEntry(Instant at, String actor, String action, String target) {}
}
