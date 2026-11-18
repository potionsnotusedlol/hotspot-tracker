package ru.moscow.hotspot_tracker.main.report;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Задача генерации отчёта (таблица report_job). */
@Entity
@Table(name = "report_job")
public class ReportJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    private String parameters;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private String filePath;

    private String errorMessage;

    private String ownerEmail;

    private Instant createdAt;

    private Instant finishedAt;

    private Instant expiresAt;

    protected ReportJob() {
        // Для JPA
    }

    ReportJob(UUID id, ReportType reportType, String parameters, String ownerEmail, Instant createdAt) {
        this.id = id;
        this.reportType = reportType;
        this.parameters = parameters;
        this.ownerEmail = ownerEmail;
        this.createdAt = createdAt;
        this.status = ReportStatus.PENDING;
    }

    void markRunning() {
        this.status = ReportStatus.RUNNING;
    }

    void markDone(String filePath, Instant finishedAt, Instant expiresAt) {
        this.status = ReportStatus.DONE;
        this.filePath = filePath;
        this.finishedAt = finishedAt;
        this.expiresAt = expiresAt;
    }

    void markFailed(String message, Instant finishedAt) {
        this.status = ReportStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public String getParameters() {
        return parameters;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
