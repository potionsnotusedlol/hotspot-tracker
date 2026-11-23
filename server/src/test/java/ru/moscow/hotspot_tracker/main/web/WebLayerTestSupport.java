package ru.moscow.hotspot_tracker.main.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.moscow.hotspot_tracker.main.report.ReportService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.AccessPointView;
import ru.moscow.hotspot_tracker.main.web.port.AuditLogService;
import ru.moscow.hotspot_tracker.main.web.port.ChatService;
import ru.moscow.hotspot_tracker.main.web.port.ImportJobService;
import ru.moscow.hotspot_tracker.main.web.port.LoadLevel;
import ru.moscow.hotspot_tracker.main.web.port.ModelParamService;
import ru.moscow.hotspot_tracker.main.web.port.StatsService;
import ru.moscow.hotspot_tracker.main.web.port.TransportType;
import ru.moscow.hotspot_tracker.main.web.port.UsageEventService;
import ru.moscow.hotspot_tracker.main.web.port.UsageEventService.UsageSummary;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService;

/**
 * Common setup for the web layer tests (FR-WEB-01, FR-WEB-07).
 *
 * <p>Starts only the MVC slice: all controllers, {@link SharedModelAdvice}, the real {@link SecurityConfig}
 * and the Thymeleaf templates from {@code client/templates}. The service ports and {@link ReportService} are
 * Mockito mocks, so the tests check what the web layer does with the ports, not the placeholder data.
 *
 * <p>All subclasses share one Spring context: do not add bean overrides in a subclass.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebjarAssets.class})
abstract class WebLayerTestSupport {

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected AccessPointService accessPoints;

    @MockitoBean
    protected StatsService stats;

    @MockitoBean
    protected UserAccountService users;

    @MockitoBean
    protected ModelParamService modelParams;

    @MockitoBean
    protected ImportJobService imports;

    @MockitoBean
    protected AuditLogService audit;

    @MockitoBean
    protected UsageEventService usage;

    @MockitoBean
    protected ChatService chat;

    @MockitoBean
    protected ReportService reports;

    /** Defaults that let every page render: an empty catalog and zero usage. */
    @BeforeEach
    void defaultPorts() {
        doAnswer(inv -> new PageImpl<AccessPointView>(List.of(), inv.<Pageable>getArgument(1), 0))
                .when(accessPoints)
                .search(any(), any());
        when(usage.summary(any(), any())).thenReturn(new UsageSummary(0, 0, 0, 0, 0, 0));
    }

    /** A catalog entry with the given id and name; the other fields are fixed sample values. */
    static AccessPointView point(long id, String name) {
        return new AccessPointView(
                id,
                name,
                "Address " + id,
                "Okrug A",
                "District A",
                TransportType.BUS,
                150,
                120.0,
                LoadLevel.MEDIUM,
                55.75,
                37.61);
    }
}
