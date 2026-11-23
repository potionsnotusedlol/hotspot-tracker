package ru.moscow.hotspot_tracker.main.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import ru.moscow.hotspot_tracker.main.web.port.AccessPointService.HourlyLoad;

/**
 * Access rules and security headers of the web chain (FR-AUTH-04, FR-WEB-05, FR-WEB-07).
 *
 * <p>Contract from {@link SecurityConfig} and the PR description: catalog, card, map and statistics are public;
 * reports, chat and profile need a login; {@code /admin/**} is for ADMIN only; forms need a CSRF token; CSP has
 * no {@code unsafe-inline}; HSTS and {@code X-Frame-Options: DENY} are set; {@code /api/**} is left to the API
 * chain.
 */
class WebSecurityTest extends WebLayerTestSupport {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/catalog",
                "/catalog/rows",
                "/catalog/districts",
                "/map",
                "/map/points",
                "/stats",
                "/login",
                "/register"
            })
    void publicPagesAreOpenForGuests(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void accessPointCardIsOpenForGuests() throws Exception {
        when(accessPoints.findById(5)).thenReturn(Optional.of(point(5, "Point 5")));
        when(accessPoints.loadProfile(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(flatProfile());

        mvc.perform(get("/access-points/5")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports", "/reports/list", "/profile", "/admin"})
    void protectedPagesSendGuestsToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void reportFileSendsGuestsToLogin() throws Exception {
        mvc.perform(get("/reports/{id}/file", UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void chatSendsGuestsToLogin() throws Exception {
        mvc.perform(post("/chat/widget").param("message", "hello").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ANALYST", "GUEST"})
    void adminPanelIsForbiddenForOtherRoles(String role) throws Exception {
        mvc.perform(get("/admin").with(user("someone@example.com").roles(role))).andExpect(status().isForbidden());
    }

    @Test
    void adminActionsAreForbiddenForUsers() throws Exception {
        mvc.perform(post("/admin/users/{id}/role", 2)
                        .param("role", "ADMIN")
                        .with(csrf())
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/model-params")
                        .param("key", "k")
                        .param("value", "v")
                        .with(csrf())
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPanelIsOpenForAdmin() throws Exception {
        mvc.perform(get("/admin").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/reports", "/profile"})
    void signedInUserSeesPersonalPages(String path) throws Exception {
        mvc.perform(get(path).with(user("user@example.com").roles("USER"))).andExpect(status().isOk());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        when(chat.reply(anyString(), anyString())).thenReturn("answer");

        mvc.perform(post("/chat/widget")
                        .param("message", "hello")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/register")
                        .param("email", "new@example.com")
                        .param("password", "password-123")
                        .param("passwordRepeat", "password-123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reports")
                        .param("type", "MOSCOW")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedLoginReturnsToLoginPageWithError() throws Exception {
        mvc.perform(formLogin("/login").user("nobody@example.com").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutReturnsToHomePage() throws Exception {
        mvc.perform(post("/logout").with(csrf()).with(user("user@example.com").roles("USER")))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void contentSecurityPolicyForbidsInlineCode() throws Exception {
        MvcResult result = mvc.perform(get("/")).andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp)
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("style-src 'self'")
                .contains("object-src 'none'")
                .contains("frame-ancestors 'none'")
                .doesNotContain("unsafe-inline")
                .doesNotContain("unsafe-eval");
    }

    @Test
    void framingAndSniffingAreDisabled() throws Exception {
        mvc.perform(get("/"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void httpsResponsesCarryHsts() throws Exception {
        mvc.perform(get("/").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/access-points", "/api/v1/auth/login"})
    void webChainDoesNotHandleApiPaths(String path) throws Exception {
        MvcResult get = mvc.perform(get(path)).andReturn();
        MvcResult postWithoutCsrf = mvc.perform(post(path)).andReturn();

        for (MvcResult result : List.of(get, postWithoutCsrf)) {
            assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                    .as("web chain headers on %s", path)
                    .isNull();
            assertThat(result.getResponse().getHeader("Location"))
                    .as("redirect to the login form on %s", path)
                    .isNull();
            assertThat(result.getResponse().getStatus())
                    .as("CSRF rejection on %s", path)
                    .isNotEqualTo(403);
        }
    }

    @Test
    void chatWidgetIsShownOnlyToSignedInUsers() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(not(containsString("chat-widget"))));
        mvc.perform(get("/").with(user("user@example.com").roles("USER")))
                .andExpect(content().string(containsString("chat-widget")));
    }

    @Test
    void adminLinkIsShownOnlyToAdmin() throws Exception {
        mvc.perform(get("/").with(user("user@example.com").roles("USER")))
                .andExpect(content().string(not(containsString("href=\"/admin\""))));
        mvc.perform(get("/").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(content().string(containsString("href=\"/admin\"")));
    }

    private static List<HourlyLoad> flatProfile() {
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(h -> new HourlyLoad(h, 10))
                .toList();
    }
}
