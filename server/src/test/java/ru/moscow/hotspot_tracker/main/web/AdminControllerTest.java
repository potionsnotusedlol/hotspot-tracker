package ru.moscow.hotspot_tracker.main.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.moscow.hotspot_tracker.main.web.port.AuditLogService.AuditEntry;
import ru.moscow.hotspot_tracker.main.web.port.ModelParamService.ModelParam;
import ru.moscow.hotspot_tracker.main.web.port.Role;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService.UserAccount;

/**
 * Admin panel (FR-ADM-01\...03, FR-LOAD-04, FR-REP-05).
 *
 * <p>Contract: the panel lists users, model parameters, import and audit logs and usage for 30 days; role and
 * parameter changes are made in the name of the signed-in admin; an admin cannot take the ADMIN role from
 * themselves; a parameter value is trimmed and must be 1 to 100 characters; errors of the ports are shown to the
 * admin, not thrown.
 */
class AdminControllerTest extends WebLayerTestSupport {

    private static final String ADMIN = "admin@example.com";

    @Test
    void dashboardShowsUsersParametersLogsAndThirtyDaysOfUsage() throws Exception {
        when(users.findAll()).thenReturn(List.of(new UserAccount(1, ADMIN, Role.ADMIN)));
        when(modelParams.findAll())
                .thenReturn(List.of(new ModelParam("k", "v", 3, ADMIN, Instant.parse("2026-11-20T10:00:00Z"))));
        when(audit.recent(50))
                .thenReturn(List.of(new AuditEntry(Instant.parse("2026-11-20T10:00:00Z"), ADMIN, "LOGIN", "-")));

        Map<String, Object> page = mvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("users", List.of(new UserAccount(1, ADMIN, Role.ADMIN))))
                .andExpect(model().attribute("usageDays", 30))
                .andReturn()
                .getModelAndView()
                .getModel();
        assertThat((List<?>) page.get("params"))
                .singleElement()
                .extracting("key", "value")
                .containsExactly("k", "v");
        assertThat((List<?>) page.get("audit"))
                .singleElement()
                .extracting("actor")
                .isEqualTo(ADMIN);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(usage).summary(from.capture(), to.capture());
        assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofDays(30));
        verify(imports).recent(20);
    }

    @Test
    void adminCannotTakeAdminRoleFromThemselves() throws Exception {
        when(users.findAll()).thenReturn(List.of(new UserAccount(1, ADMIN, Role.ADMIN)));

        mvc.perform(post("/admin/users/1/role")
                        .param("role", "USER")
                        .with(csrf())
                        .with(admin()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("error"));

        verify(users, never()).changeRole(anyLong(), any(), anyString());
    }

    @Test
    void roleChangeIsMadeInTheNameOfTheAdmin() throws Exception {
        when(users.findAll())
                .thenReturn(List.of(
                        new UserAccount(1, ADMIN, Role.ADMIN), new UserAccount(2, "user@example.com", Role.USER)));

        mvc.perform(post("/admin/users/2/role")
                        .param("role", "ANALYST")
                        .with(csrf())
                        .with(admin()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"))
                .andExpect(flash().attributeCount(1));

        verify(users).changeRole(2, Role.ANALYST, ADMIN);
    }

    @Test
    void roleChangeErrorIsShownToTheAdmin() throws Exception {
        doThrow(new IllegalArgumentException("not available")).when(users).changeRole(2, Role.ADMIN, ADMIN);

        mvc.perform(post("/admin/users/2/role")
                        .param("role", "ADMIN")
                        .with(csrf())
                        .with(admin()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", "not available"));
    }

    @Test
    void unknownRoleIsBadRequest() throws Exception {
        mvc.perform(post("/admin/users/2/role")
                        .param("role", "ROOT")
                        .with(csrf())
                        .with(admin()))
                .andExpect(status().isBadRequest());
        verify(users, never()).changeRole(anyLong(), any(), anyString());
    }

    @Test
    void parameterValueIsTrimmedAndSavedInTheNameOfTheAdmin() throws Exception {
        mvc.perform(post("/admin/model-params")
                        .param("key", "household_size")
                        .param("value", "  2.4  ")
                        .with(csrf())
                        .with(admin()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));

        verify(modelParams).update("household_size", "2.4", ADMIN);
    }

    @Test
    void parameterValueOfOneHundredCharactersIsAccepted() throws Exception {
        String value = "x".repeat(100);

        mvc.perform(post("/admin/model-params")
                        .param("key", "k")
                        .param("value", value)
                        .with(csrf())
                        .with(admin()))
                .andExpect(flash().attributeExists("message"));

        verify(modelParams).update("k", value, ADMIN);
    }

    @Test
    void blankOrTooLongParameterValueIsRejected() throws Exception {
        for (String value : List.of("", "   ", "x".repeat(101))) {
            mvc.perform(post("/admin/model-params")
                            .param("key", "k")
                            .param("value", value)
                            .with(csrf())
                            .with(admin()))
                    .andExpect(redirectedUrl("/admin"))
                    .andExpect(flash().attributeExists("error"));
        }

        verify(modelParams, never()).update(anyString(), anyString(), anyString());
    }

    @Test
    void parameterErrorOfThePortIsShownToTheAdmin() throws Exception {
        doThrow(new IllegalArgumentException("unknown key")).when(modelParams).update("k", "v", ADMIN);

        mvc.perform(post("/admin/model-params")
                        .param("key", "k")
                        .param("value", "v")
                        .with(csrf())
                        .with(admin()))
                .andExpect(flash().attribute("error", "unknown key"));
    }

    private static RequestPostProcessor admin() {
        return user(ADMIN).roles("ADMIN");
    }
}
