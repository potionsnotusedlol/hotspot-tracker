package ru.moscow.hotspot_tracker.main.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Report queue, access to reports and retention (FR-REP-05\...07).
 *
 * <p>Contract from {@link ReportService}: a request is checked and saved as PENDING, then handed to the worker;
 * a job the queue rejects becomes FAILED at once; unfinished jobs are queued again after a restart; the owner sees
 * own reports, the admin sees all, a foreign report looks like a missing one; a file is given only when the report
 * is DONE; expired reports are deleted with their files.
 */
class ReportServiceTest {

    private static final String OWNER = "owner@example.com";

    @TempDir
    Path tempDir;

    private ReportJobRepository jobs;
    private ReportWorker worker;
    private ReportService service;

    @BeforeEach
    void setUp() {
        jobs = mock(ReportJobRepository.class);
        worker = mock(ReportWorker.class);
        when(jobs.save(any())).then(AdditionalAnswers.returnsFirstArg());
        service = new ReportService(jobs, worker);
    }

    @Test
    void requestSavesPendingJobOfTheOwnerAndHandsItToTheWorker() {
        ReportJob job =
                service.request(ReportType.DISTRICT, new ReportParams(null, "Okrug", "District", null), OWNER, false);

        assertThat(job.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(job.getOwnerEmail()).isEqualTo(OWNER);
        assertThat(job.getReportType()).isEqualTo(ReportType.DISTRICT);
        assertThat(job.getCreatedAt()).isCloseTo(Instant.now(), within());
        assertThat(ReportParams.fromQuery(job.getParameters()))
                .isEqualTo(new ReportParams(null, "Okrug", "District", null));
        verify(jobs).save(job);
        verify(worker).generate(job.getId());
    }

    @Test
    void eachRequestGetsItsOwnId() {
        ReportJob first = service.request(ReportType.MOSCOW, empty(), OWNER, false);
        ReportJob second = service.request(ReportType.MOSCOW, empty(), OWNER, false);

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void usageReportIsForAdminOnly() {
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> service.request(ReportType.USAGE, empty(), OWNER, false));

        verify(jobs, never()).save(any());
        verify(worker, never()).generate(any());
    }

    @Test
    void adminCanRequestUsageReport() {
        ReportJob job = service.request(ReportType.USAGE, new ReportParams(null, null, null, 7), "admin@x.ru", true);

        assertThat(job.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(worker).generate(job.getId());
    }

    @Test
    void invalidParametersAreRejectedBeforeSaving() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.request(ReportType.ACCESS_POINT, empty(), OWNER, false));

        verify(jobs, never()).save(any());
        verify(worker, never()).generate(any());
    }

    @Test
    void jobRejectedByFullQueueFailsAtOnceWithRetention() {
        doThrow(new TaskRejectedException("full")).when(worker).generate(any());

        ReportJob job = service.request(ReportType.MOSCOW, empty(), OWNER, false);

        assertThat(job.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotBlank();
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getExpiresAt()).isEqualTo(job.getFinishedAt().plus(Duration.ofDays(7)));
        ArgumentCaptor<ReportJob> saved = ArgumentCaptor.forClass(ReportJob.class);
        verify(jobs, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().getStatus()).isEqualTo(ReportStatus.FAILED);
    }

    @Test
    void unfinishedJobsAreQueuedAgainAfterRestart() {
        ReportJob pending = job(ReportStatus.PENDING, OWNER);
        ReportJob running = job(ReportStatus.RUNNING, OWNER);
        when(jobs.findByStatusIn(any())).thenReturn(List.of(pending, running));

        service.requeueUnfinished();

        ArgumentCaptor<java.util.Collection<ReportStatus>> statuses = ArgumentCaptor.captor();
        verify(jobs).findByStatusIn(statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(ReportStatus.PENDING, ReportStatus.RUNNING);
        assertThat(running.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(worker).generate(pending.getId());
        verify(worker).generate(running.getId());
    }

    @Test
    void requeueRejectedByFullQueueFailsTheJob() {
        ReportJob running = job(ReportStatus.RUNNING, OWNER);
        when(jobs.findByStatusIn(any())).thenReturn(List.of(running));
        doThrow(new TaskRejectedException("full")).when(worker).generate(running.getId());

        service.requeueUnfinished();

        assertThat(running.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(running.getExpiresAt()).isNotNull();
    }

    @Test
    void listShowsTheLatestJobsOfTheOwner() {
        List<ReportJob> latest = List.of(job(ReportStatus.DONE, OWNER));
        when(jobs.findTop20ByOwnerEmailOrderByCreatedAtDesc(OWNER)).thenReturn(latest);

        assertThat(service.listFor(OWNER)).isEqualTo(latest);
    }

    @Test
    void ownerFindsOwnReport() {
        ReportJob job = stored(job(ReportStatus.DONE, OWNER));

        assertThat(service.find(job.getId(), OWNER, false)).isSameAs(job);
    }

    @Test
    void foreignReportLooksLikeMissingOne() {
        ReportJob job = stored(job(ReportStatus.DONE, OWNER));
        UUID missing = UUID.randomUUID();
        when(jobs.findById(missing)).thenReturn(Optional.empty());

        NoSuchElementException foreign = org.junit.jupiter.api.Assertions.assertThrows(
                NoSuchElementException.class, () -> service.find(job.getId(), "other@example.com", false));
        NoSuchElementException absent = org.junit.jupiter.api.Assertions.assertThrows(
                NoSuchElementException.class, () -> service.find(missing, "other@example.com", false));
        assertThat(foreign.getMessage()).isEqualTo(absent.getMessage());
    }

    @Test
    void adminFindsAnyReport() {
        ReportJob job = stored(job(ReportStatus.DONE, OWNER));

        assertThat(service.find(job.getId(), "admin@example.com", true)).isSameAs(job);
    }

    @Test
    void ownerEmailIsComparedExactly() {
        ReportJob job = stored(job(ReportStatus.DONE, OWNER));

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> service.find(job.getId(), "OWNER@example.com", false));
    }

    @Test
    void readyFileIsGivenForDoneReport() {
        ReportJob job = job(ReportStatus.PENDING, OWNER);
        job.markDone(tempDir.resolve("r.xlsx").toString(), Instant.now());
        stored(job);

        assertThat(service.readyFile(job.getId(), OWNER, false)).isEqualTo(tempDir.resolve("r.xlsx"));
    }

    @Test
    void unfinishedOrFailedReportHasNoFile() {
        for (ReportStatus status : List.of(ReportStatus.PENDING, ReportStatus.RUNNING)) {
            ReportJob job = stored(job(status, OWNER));
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> service.readyFile(job.getId(), OWNER, false));
        }
        ReportJob failed = job(ReportStatus.PENDING, OWNER);
        failed.markFailed("error", Instant.now());
        stored(failed);
        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> service.readyFile(failed.getId(), OWNER, false));
    }

    @Test
    void foreignReportFileIsNotGiven() {
        ReportJob job = job(ReportStatus.PENDING, OWNER);
        job.markDone(tempDir.resolve("r.xlsx").toString(), Instant.now());
        stored(job);

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> service.readyFile(job.getId(), "other@example.com", false));
    }

    @Test
    void expiredReportsAreDeletedWithTheirFiles() throws Exception {
        Path file = Files.createFile(tempDir.resolve("expired.xlsx"));
        ReportJob done = job(ReportStatus.PENDING, OWNER);
        done.markDone(file.toString(), Instant.now().minus(Duration.ofDays(8)));
        ReportJob failed = job(ReportStatus.PENDING, OWNER);
        failed.markFailed("error", Instant.now().minus(Duration.ofDays(8)));
        ReportJob fileAlreadyGone = job(ReportStatus.PENDING, OWNER);
        fileAlreadyGone.markDone(
                tempDir.resolve("gone.xlsx").toString(), Instant.now().minus(Duration.ofDays(9)));
        when(jobs.findByExpiresAtBefore(any())).thenReturn(List.of(done, failed, fileAlreadyGone));

        service.removeExpired();

        assertThat(file).doesNotExist();
        verify(jobs).delete(done);
        verify(jobs).delete(failed);
        verify(jobs).delete(fileAlreadyGone);
        ArgumentCaptor<Instant> moment = ArgumentCaptor.forClass(Instant.class);
        verify(jobs).findByExpiresAtBefore(moment.capture());
        assertThat(moment.getValue()).isCloseTo(Instant.now(), within());
    }

    private ReportJob stored(ReportJob job) {
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    private static ReportJob job(ReportStatus status, String owner) {
        ReportJob job = new ReportJob(UUID.randomUUID(), ReportType.MOSCOW, "", owner, Instant.now());
        if (status == ReportStatus.RUNNING) {
            job.markRunning();
        }
        return job;
    }

    private static ReportParams empty() {
        return new ReportParams(null, null, null, null);
    }

    private static org.assertj.core.data.TemporalUnitOffset within() {
        return org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS);
    }
}
