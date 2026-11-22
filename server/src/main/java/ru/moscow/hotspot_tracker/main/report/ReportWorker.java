package ru.moscow.hotspot_tracker.main.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Фоновая генерация отчёта. Сейчас работает в том же приложении, в пуле reportExecutor.
 * По ТЗ это задача профиля worker, перенос туда не меняет код.
 */
@Component
class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

    private final ReportJobRepository jobs;
    private final ExcelReportWriter writer;
    private final Path storageDir;

    ReportWorker(ReportJobRepository jobs, ExcelReportWriter writer,
                 @Value("${hotspot.reports.storage-dir}") String storageDir) {
        this.jobs = jobs;
        this.writer = writer;
        this.storageDir = Path.of(storageDir).toAbsolutePath();
    }

    @Async("reportExecutor")
    void generate(UUID jobId) {
        ReportJob job = jobs.findById(jobId).orElseThrow();
        job.markRunning();
        jobs.save(job);

        Path target = storageDir.resolve(jobId + ".xlsx");
        try {
            writer.write(job.getReportType(), ReportParams.fromQuery(job.getParameters()), target);
            job.markDone(target.toString(), Instant.now());
        } catch (IllegalArgumentException e) {
            // Текст этой ошибки написан для пользователя, его можно показать
            deleteQuietly(target);
            job.markFailed(e.getMessage(), Instant.now());
        } catch (IOException | RuntimeException e) {
            log.error("Не удалось сформировать отчёт {}", jobId, e);
            deleteQuietly(target);
            job.markFailed("Не удалось сформировать отчёт, попробуйте позже", Instant.now());
        }
        jobs.save(job);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Не удалось удалить файл {}", file, e);
        }
    }
}
