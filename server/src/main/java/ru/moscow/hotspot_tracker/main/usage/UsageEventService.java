package ru.moscow.hotspot_tracker.main.usage;

import java.time.Instant;

/** Учёт использования по каналам web / android / telegram / chat (FR-REP-05). Владелец — №3. */
public interface UsageEventService {

    UsageSummary summary(Instant from, Instant to);

    record UsageSummary(long users, long webRequests, long apiRequests, long botRequests,
                        long chatRequests, long errors) {}
}
