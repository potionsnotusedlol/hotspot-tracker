package ru.moscow.hotspot_tracker.main.web.port;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** ЗАГЛУШКА веба: события использования пока не пишутся, все счётчики нули. */
@ConditionalOnProperty(prefix = "hotspot.web", name = "placeholders", havingValue = "true", matchIfMissing = true)
@Service
class PlaceholderUsageEventService implements UsageEventService {

    @Override
    public UsageSummary summary(Instant from, Instant to) {
        return new UsageSummary(0, 0, 0, 0, 0, 0);
    }
}
