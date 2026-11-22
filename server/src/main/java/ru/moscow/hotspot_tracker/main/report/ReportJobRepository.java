package ru.moscow.hotspot_tracker.main.report;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {

    List<ReportJob> findTop20ByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    List<ReportJob> findByExpiresAtBefore(Instant moment);

    List<ReportJob> findByStatusIn(Collection<ReportStatus> statuses);
}
