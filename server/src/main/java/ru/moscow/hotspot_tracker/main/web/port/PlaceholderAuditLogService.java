package ru.moscow.hotspot_tracker.main.web.port;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** ЗАГЛУШКА веба: журнала аудита пока нет, список пустой. */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderAuditLogService implements AuditLogService {

    @Override
    public List<AuditEntry> recent(int limit) {
        return List.of();
    }
}
