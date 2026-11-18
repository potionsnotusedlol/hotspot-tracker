package ru.moscow.hotspot_tracker.main.usage;

import java.time.Instant;
import org.springframework.stereotype.Service;

/** ВРЕМЕННАЯ ЗАГЛУШКА: событий пока не пишем, все счётчики нули. Удалить с появлением usage_event (№3). */
@Service
class StubUsageEventService implements UsageEventService {

    @Override
    public UsageSummary summary(Instant from, Instant to) {
        return new UsageSummary(0, 0, 0, 0, 0, 0);
    }
}
