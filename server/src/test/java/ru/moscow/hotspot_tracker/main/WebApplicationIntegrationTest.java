package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.moscow.hotspot_tracker.main.report.ReportJob;
import ru.moscow.hotspot_tracker.main.report.ReportService;
import ru.moscow.hotspot_tracker.main.report.ReportStatus;

/**
 * The web interface and reports on the real application with a database (FR-WEB-01, FR-REP-01\...07).
 *
 * <p>Runs the whole context with the placeholder ports ({@code hotspot.web.placeholders=true}), the Flyway
 * migration of {@code report_job} and the real report pool. Contract from the PR description: every page shows
 * the placeholder warning; a report goes from the queue to a downloadable {@code .xlsx}; only the owner and the
 * admin can download it; failed reports show an error; expired reports are removed with their files; the
 * placeholders refuse registration, login and role changes instead of pretending to work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebApplicationIntegrationTest {

    private static final String OWNER = "owner@example.com";

    /** "Central okrug" in Russian: an okrug of the placeholder catalog. */
    private static final String PLACEHOLDER_OKRUG = "Центральный округ";

    private static final Duration REPORT_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    static Path reportsDir;

    @Autowired
    MockMvc mvc;

    @Autowired
    ReportService reports;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void reportStorage(DynamicPropertyRegistry registry) {
        registry.add("hotspot.reports.storage-dir", () -> reportsDir.toString());
    }

    @Test
    void publicPagesRenderWithPlaceholderWarning() throws Exception {
        for (String path : List.of("/", "/catalog", "/map", "/stats", "/login", "/register")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("alert-warning")));
        }
    }

    @Test
    void catalogCardMapAndStatisticsWorkOnPlaceholderData() throws Exception {
        mvc.perform(get("/catalog").param("okrug", PLACEHOLDER_OKRUG).param("sort", "estimatedLoad,desc"))
                .andExpect(status().isOk());
        mvc.perform(get("/catalog/rows").param("q", "1").param("size", "5").param("page", "1"))
                .andExpect(status().isOk());
        mvc.perform(get("/catalog/districts").param("okrug", PLACEHOLDER_OKRUG)).andExpect(status().isOk());
        mvc.perform(get("/access-points/5")).andExpect(status().isOk());
        mvc.perform(get("/access-points/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/map/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
        mvc.perform(get("/stats").param("okrug", PLACEHOLDER_OKRUG).param("transportType", "BUS"))
                .andExpect(status().isOk());
    }

    @Test
    void reportIsGeneratedAndOnlyOwnerAndAdminCanDownloadIt() throws Exception {
        mvc.perform(post("/reports").param("type", "MOSCOW").with(csrf()).with(owner()))
                .andExpect(redirectedUrl("/reports"));

        ReportJob job = awaitFinished(latestJob(OWNER));

        assertThat(job.getStatus()).isEqualTo(ReportStatus.DONE);
        assertThat(Path.of(job.getFilePath()))
                .startsWith(reportsDir.toAbsolutePath())
                .exists();
        byte[] file = mvc.perform(get("/reports/{id}/file", job.getId()).with(owner()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(file).startsWith('P', 'K');
        mvc.perform(get("/reports/{id}/file", job.getId())
                        .with(user("other@example.com").roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/{id}/file", job.getId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void reportOfMissingAccessPointFailsWithError() throws Exception {
        String owner = "failing@example.com";
        mvc.perform(post("/reports")
                        .param("type", "ACCESS_POINT")
                        .param("accessPointId", "999999")
                        .with(csrf())
                        .with(user(owner).roles("USER")))
                .andExpect(redirectedUrl("/reports"));

        ReportJob job = awaitFinished(latestJob(owner));

        assertThat(job.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotBlank();
        assertThat(job.getExpiresAt()).isNotNull();
        mvc.perform(get("/reports/{id}/file", job.getId()).with(user(owner).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/list").with(user(owner).roles("USER")))
                .andExpect(model().attribute("hasActive", false))
                .andExpect(content().string(containsString(job.getErrorMessage())));
    }

    @Test
    void usageReportIsRefusedToUser() throws Exception {
        String owner = "curious@example.com";

        mvc.perform(post("/reports")
                        .param("type", "USAGE")
                        .with(csrf())
                        .with(user(owner).roles("USER")))
                .andExpect(redirectedUrl("/reports"))
                .andExpect(flash().attributeExists("error"));

        assertThat(reports.listFor(owner)).isEmpty();
    }

    @Test
    void expiredReportIsRemovedWithItsFile() throws Exception {
        String owner = "expiring@example.com";
        mvc.perform(post("/reports")
                .param("type", "MOSCOW")
                .with(csrf())
                .with(user(owner).roles("USER")));
        ReportJob job = awaitFinished(latestJob(owner));
        Path file = Path.of(job.getFilePath());
        assertThat(file).exists();
        jdbc.update("UPDATE report_job SET expires_at = now() - interval '1 minute' WHERE id = ?", job.getId());

        reports.removeExpired();

        assertThat(file).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_job WHERE id = ?", Long.class, job.getId()))
                .isZero();
    }

    @Test
    void listShowsTwentyLatestReportsOfTheOwnerOnly() throws Exception {
        String owner = "busy@example.com";
        for (int i = 0; i < 21; i++) {
            mvc.perform(post("/reports")
                    .param("type", "MOSCOW")
                    .with(csrf())
                    .with(user(owner).roles("USER")));
        }

        List<ReportJob> listed = reports.listFor(owner);

        assertThat(listed).hasSize(20).allMatch(j -> j.getOwnerEmail().equals(owner));
        assertThat(listed).isSortedAccordingTo((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        await().atMost(REPORT_TIMEOUT)
                .until(() -> reports.listFor(owner).stream()
                        .noneMatch(
                                j -> j.getStatus() == ReportStatus.PENDING || j.getStatus() == ReportStatus.RUNNING));
    }

    @Test
    void placeholdersRefuseRegistrationLoginAndRoleChanges() throws Exception {
        mvc.perform(post("/register")
                        .param("email", "new@example.com")
                        .param("password", "correct-horse-1")
                        .param("passwordRepeat", "correct-horse-1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("error"));
        mvc.perform(formLogin("/login").user("new@example.com").password("correct-horse-1"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/admin/users/2/role")
                        .param("role", "ANALYST")
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/model-params")
                        .param("key", "k")
                        .param("value", "v")
                        .with(csrf())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void adminPanelAndChatWorkOnPlaceholders() throws Exception {
        mvc.perform(get("/admin").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/chat/widget")
                        .param("message", "hello")
                        .with(csrf())
                        .with(user(OWNER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("answer"));
    }

    private UUID latestJob(String owner) {
        List<ReportJob> jobs = reports.listFor(owner);
        assertThat(jobs).as("reports of %s", owner).isNotEmpty();
        return jobs.getFirst().getId();
    }

    private ReportJob awaitFinished(UUID id) {
        return await().atMost(REPORT_TIMEOUT)
                .until(
                        () -> reports.find(id, "admin@example.com", true),
                        job -> job.getStatus() == ReportStatus.DONE || job.getStatus() == ReportStatus.FAILED);
    }

    private static RequestPostProcessor owner() {
        return user(OWNER).roles("USER");
    }
}
