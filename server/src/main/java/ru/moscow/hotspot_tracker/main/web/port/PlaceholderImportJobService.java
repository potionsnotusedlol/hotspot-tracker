package ru.moscow.hotspot_tracker.main.web.port;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** ЗАГЛУШКА веба: импорт ещё не реализован, журнал пустой. */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderImportJobService implements ImportJobService {

    @Override
    public List<ImportJob> recent(int limit) {
        return List.of();
    }
}
