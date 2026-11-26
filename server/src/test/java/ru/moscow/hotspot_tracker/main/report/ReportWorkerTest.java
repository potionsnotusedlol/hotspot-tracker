package ru.moscow.hotspot_tracker.main.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Background generation of one report (FR-REP-06, FR-REP-07).
 *
 * <p>Contract from {@link ReportWorker}: the job is RUNNING while the file is written; on success it is DONE with
 * the file in the storage directory and kept for 7 days; on failure it is FAILED, the partial file is removed, a
 * user-facing error ({@link IllegalArgumentException}) is shown as is and any other error gets a generic text.
 */
class ReportWorkerTest {

    @TempDir
    Path storage;

    private ReportJobRepository jobs;
    private ExcelReportWriter writer;
    private ReportWorker worker;
    private ReportJob job;
    private final List<ReportStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jobs = mock(ReportJobRepository.class);
        writer = mock(ExcelReportWriter.class);
        worker = new ReportWorker(jobs, writer, storage.toString());
        job = new ReportJob(
                UUID.randomUUID(),
                ReportType.DISTRICT,
                new ReportParams(null, "Okrug", "District", null).toQuery(),
                "owner@example.com",
                Instant.now());
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        doAnswer(inv -> {
                    savedStatuses.add(inv.<ReportJob>getArgument(0).getStatus());
                    return inv.getArgument(0);
                })
                .when(jobs)
                .save(any());
    }

    @Test
    void successfulReportIsDoneWithFileKeptForSevenDays() throws Exception {
        doAnswer(inv -> Files.write(inv.<Path>getArgument(2), new byte[] {1, 2, 3}))
                .when(writer)
                .write(any(), any(), any());

        worker.generate(job.getId());

        Path expected = storage.toAbsolutePath().resolve(job.getId() + ".xlsx");
        verify(writer).write(ReportType.DISTRICT, new ReportParams(null, "Okrug", "District", null), expected);
        assertThat(savedStatuses).containsExactly(ReportStatus.RUNNING, ReportStatus.DONE);
        assertThat(job.getStatus()).isEqualTo(ReportStatus.DONE);
        assertThat(Path.of(job.getFilePath())).isEqualTo(expected).exists();
        assertThat(job.getFinishedAt())
                .isCloseTo(
                        Instant.now(),
                        org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(job.getExpiresAt()).isEqualTo(job.getFinishedAt().plus(Duration.ofDays(7)));
    }

    @Test
    void userFacingErrorIsShownAsIsAndPartialFileIsRemoved() throws Exception {
        doAnswer(inv -> {
                    Files.write(inv.<Path>getArgument(2), new byte[] {1});
                    throw new IllegalArgumentException("Point not found");
                })
                .when(writer)
                .write(any(), any(), any());

        worker.generate(job.getId());

        assertThat(job.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Point not found");
        assertThat(job.getExpiresAt()).isEqualTo(job.getFinishedAt().plus(Duration.ofDays(7)));
        assertThat(storage.resolve(job.getId() + ".xlsx")).doesNotExist();
        assertThat(savedStatuses).containsExactly(ReportStatus.RUNNING, ReportStatus.FAILED);
    }

    @Test
    void internalErrorDetailsAreNotShownToTheUser() throws Exception {
        doAnswer(inv -> {
                    Files.write(inv.<Path>getArgument(2), new byte[] {1});
                    throw new IOException("No space left on /srv/secret/reports");
                })
                .when(writer)
                .write(any(), any(), any());

        worker.generate(job.getId());

        assertThat(job.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(job.getErrorMessage())
                .isNotBlank()
                .doesNotContain("/srv/secret")
                .doesNotContain("space");
        assertThat(storage.resolve(job.getId() + ".xlsx")).doesNotExist();
    }

    @Test
    void unexpectedRuntimeErrorFailsTheJob() throws Exception {
        doAnswer(inv -> {
                    throw new IllegalStateException("bug");
                })
                .when(writer)
                .write(eq(ReportType.DISTRICT), any(), any());

        worker.generate(job.getId());

        assertThat(job.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotBlank().doesNotContain("bug");
        assertThat(savedStatuses).last().isEqualTo(ReportStatus.FAILED);
    }
}
