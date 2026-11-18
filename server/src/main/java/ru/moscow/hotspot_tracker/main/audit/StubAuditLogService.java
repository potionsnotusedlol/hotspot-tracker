package ru.moscow.hotspot_tracker.main.audit;

import java.util.List;
import org.springframework.stereotype.Service;

/** ВРЕМЕННАЯ ЗАГЛУШКА: журнала пока нет, возвращаем пустой список. Удалить с появлением модуля аудита (№3). */
@Service
class StubAuditLogService implements AuditLogService {

    @Override
    public List<AuditEntry> recent(int limit) {
        return List.of();
    }
}
