package ru.moscow.hotspot_tracker.main.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отчёты: постановка в очередь, статус, скачивание (FR-REP-06). Владелец видит свои отчёты,
 * администратор видит все. Чужой отчёт выглядит как несуществующий, чтобы не раскрывать id.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportJobRepository jobs;
    private final ReportWorker worker;

    ReportService(ReportJobRepository jobs, ReportWorker worker) {
        this.jobs = jobs;
        this.worker = worker;
    }

    /** Ставит отчёт в очередь и сразу возвращает задачу в статусе PENDING. */
    public ReportJob request(ReportType type, ReportParams params, String owner, boolean admin) {
        if (type.adminOnly() && !admin) {
            throw new AccessDeniedException("Этот отчёт доступен только администратору");
        }
        params.validate(type);
        ReportJob job = jobs.save(new ReportJob(UUID.randomUUID(), type, params.toQuery(), owner, Instant.now()));
        enqueue(job);
        return job;
    }

    /**
     * Ставит задачу в пул генерации. Если очередь полна, задача сразу получает статус FAILED,
     * а не остаётся в PENDING: иначе страница будет опрашивать её вечно.
     */
    private void enqueue(ReportJob job) {
        try {
            worker.generate(job.getId());
        } catch (TaskRejectedException e) {
            log.warn("Очередь отчётов заполнена, задача {} отклонена", job.getId());
            job.markFailed("Очередь отчётов заполнена, попробуйте через несколько минут", Instant.now());
            jobs.save(job);
        }
    }

    /**
     * После перезапуска задачи в PENDING и RUNNING снова ставим в очередь.
     * Без этого они остались бы незавершёнными навсегда.
     */
    @EventListener(ApplicationReadyEvent.class)
    void requeueUnfinished() {
        for (ReportJob job : jobs.findByStatusIn(List.of(ReportStatus.PENDING, ReportStatus.RUNNING))) {
            job.markPending();
            jobs.save(job);
            enqueue(job);
        }
    }

    public List<ReportJob> listFor(String owner) {
        return jobs.findTop20ByOwnerEmailOrderByCreatedAtDesc(owner);
    }

    public ReportJob find(UUID id, String user, boolean admin) {
        ReportJob job = jobs.findById(id).orElseThrow(() -> new NoSuchElementException("Отчёт не найден"));
        if (!admin && !job.getOwnerEmail().equals(user)) {
            throw new NoSuchElementException("Отчёт не найден");
        }
        return job;
    }

    /** Путь к готовому файлу. Пока отчёт не готов, файла нет. */
    public Path readyFile(UUID id, String user, boolean admin) {
        ReportJob job = find(id, user, admin);
        if (job.getStatus() != ReportStatus.DONE) {
            throw new NoSuchElementException("Отчёт ещё не готов");
        }
        return Path.of(job.getFilePath());
    }

    /** Удаляет просроченные отчёты вместе с файлами (FR-REP-07). Запуск каждую ночь. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void removeExpired() {
        for (ReportJob job : jobs.findByExpiresAtBefore(Instant.now())) {
            if (job.getFilePath() != null) {
                try {
                    Files.deleteIfExists(Path.of(job.getFilePath()));
                } catch (IOException e) {
                    log.warn("Не удалось удалить просроченный файл {}", job.getFilePath(), e);
                }
            }
            jobs.delete(job);
        }
    }
}
