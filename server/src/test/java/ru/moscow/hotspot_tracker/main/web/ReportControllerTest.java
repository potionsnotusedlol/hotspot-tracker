package ru.moscow.hotspot_tracker.main.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.moscow.hotspot_tracker.main.report.ReportJob;
import ru.moscow.hotspot_tracker.main.report.ReportParams;
import ru.moscow.hotspot_tracker.main.report.ReportStatus;
import ru.moscow.hotspot_tracker.main.report.ReportType;

/**
 * Report page, creation, status polling and download (FR-REP-01\...06).
 *
 * <p>Contract: the report types offered depend on the role ({@code USAGE} for ADMIN only); a form sent by HTMX
 * gets the list fragment, a plain form gets a redirect; the owner and the admin flag passed to
 * {@link ru.moscow.hotspot_tracker.main.report.ReportService} come from the login; a missing, foreign or unfinished
 * report is a 404; a ready report is an {@code .xlsx} attachment.
 */
class ReportControllerTest extends WebLayerTestSupport {

    private static final String OWNER = "owner@example.com";

    @TempDir
    Path tempDir;

    @Test
    void userIsNotOfferedTheUsageReport() throws Exception {
        mvc.perform(get("/reports").with(owner()))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                                "reportTypes",
                                List.of(ReportType.ACCESS_POINT, ReportType.DISTRICT, ReportType.MOSCOW)));
    }

    @Test
    void adminIsOfferedAllReports() throws Exception {
        mvc.perform(get("/reports").with(admin()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("reportTypes", List.of(ReportType.values())));
    }

    @Test
    void plainFormRequestsReportForTheSignedInUserAndRedirects() throws Exception {
        mvc.perform(post("/reports")
                        .param("type", "DISTRICT")
                        .param("okrug", "Okrug A")
                        .param("district", "District A")
                        .with(csrf())
                        .with(owner()))
                .andExpect(redirectedUrl("/reports"))
                .andExpect(flash().attributeExists("message"));

        verify(reports)
                .request(ReportType.DISTRICT, new ReportParams(null, "Okrug A", "District A", null), OWNER, false);
    }

    @Test
    void adminFlagIsPassedForAdmin() throws Exception {
        mvc.perform(post("/reports")
                        .param("type", "USAGE")
                        .param("days", "7")
                        .with(csrf())
                        .with(admin()))
                .andExpect(redirectedUrl("/reports"));

        verify(reports).request(ReportType.USAGE, new ReportParams(null, null, null, 7), "admin@example.com", true);
    }

    @Test
    void htmxFormGetsTheListFragment() throws Exception {
        mvc.perform(post("/reports")
                        .param("type", "MOSCOW")
                        .header("HX-Request", "true")
                        .with(csrf())
                        .with(owner()))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("error"))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void rejectedRequestIsShownAsErrorInTheFragment() throws Exception {
        when(reports.request(any(), any(), anyString(), anyBoolean()))
                .thenThrow(new AccessDeniedException("admin only"));

        mvc.perform(post("/reports")
                        .param("type", "USAGE")
                        .header("HX-Request", "true")
                        .with(csrf())
                        .with(owner()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "admin only"))
                .andExpect(content().string(containsString("admin only")));
    }

    @Test
    void invalidParametersAreShownAfterRedirect() throws Exception {
        when(reports.request(any(), any(), anyString(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("choose an okrug"));

        mvc.perform(post("/reports").param("type", "DISTRICT").with(csrf()).with(owner()))
                .andExpect(redirectedUrl("/reports"))
                .andExpect(flash().attribute("error", "choose an okrug"));
    }

    @Test
    void unknownReportTypeIsBadRequest() throws Exception {
        mvc.perform(post("/reports").param("type", "EVERYTHING").with(csrf()).with(owner()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listShowsOnlyJobsOfTheSignedInUserAndKeepsPollingWhileOneIsRunning() throws Exception {
        ReportJob running = job(ReportStatus.RUNNING);
        when(reports.listFor(OWNER)).thenReturn(List.of(running));

        mvc.perform(get("/reports/list").with(owner()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActive", true))
                .andExpect(model().attribute("rows", hasSize(1)))
                .andExpect(content().string(not(containsString("<html"))));
        verify(reports, org.mockito.Mockito.atLeastOnce()).listFor(OWNER);
    }

    @Test
    void listStopsPollingWhenNothingIsActive() throws Exception {
        ReportJob done = job(ReportStatus.DONE);
        ReportJob failed = job(ReportStatus.FAILED);
        when(reports.listFor(OWNER)).thenReturn(List.of(done, failed));

        mvc.perform(get("/reports/list").with(owner())).andExpect(model().attribute("hasActive", false));
    }

    @Test
    void readyReportIsDownloadedAsXlsxAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = Files.write(tempDir.resolve("report.xlsx"), new byte[] {'P', 'K', 3, 4});
        when(reports.readyFile(id, OWNER, false)).thenReturn(file);

        mvc.perform(get("/reports/{id}/file", id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(
                        header().string("Content-Disposition", "attachment; filename=\"wifi-report-" + id + ".xlsx\""))
                .andExpect(content().bytes(new byte[] {'P', 'K', 3, 4}));
    }

    @Test
    void adminDownloadIsRequestedWithAdminFlag() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = Files.write(tempDir.resolve("admin.xlsx"), new byte[] {1});
        when(reports.readyFile(id, "admin@example.com", true)).thenReturn(file);

        mvc.perform(get("/reports/{id}/file", id).with(admin())).andExpect(status().isOk());
    }

    @Test
    void missingForeignOrUnfinishedReportIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(reports.readyFile(eq(id), anyString(), anyBoolean())).thenThrow(new NoSuchElementException("no"));

        mvc.perform(get("/reports/{id}/file", id).with(owner())).andExpect(status().isNotFound());
    }

    @Test
    void malformedReportIdIsBadRequest() throws Exception {
        mvc.perform(get("/reports/{id}/file", "../../etc/passwd").with(owner())).andExpect(status().is4xxClientError());
    }

    private static ReportJob job(ReportStatus status) {
        ReportJob job = mock(ReportJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getStatus()).thenReturn(status);
        when(job.getReportType()).thenReturn(ReportType.MOSCOW);
        when(job.getCreatedAt()).thenReturn(Instant.parse("2026-11-20T10:00:00Z"));
        when(job.getOwnerEmail()).thenReturn(OWNER);
        return job;
    }

    private static RequestPostProcessor owner() {
        return user(OWNER).roles("USER");
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@example.com").roles("ADMIN");
    }
}
