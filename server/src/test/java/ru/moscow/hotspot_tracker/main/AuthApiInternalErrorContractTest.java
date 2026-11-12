package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.moscow.hotspot_tracker.auth.AuthService;
import ru.moscow.hotspot_tracker.common.error.ErrorCode;

/** Tests that an unexpected failure becomes a 500 problem without internal details. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiInternalErrorContractTest {

    private static final String INTERNAL_DETAIL = "connection string with secret-value";

    @LocalServerPort
    int port;

    @MockitoBean
    AuthService authService;

    @Test
    void unexpectedFailureDoesNotRevealInternalDetails() throws Exception {
        when(authService.register(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));
        HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"user@example.com\",\"password\":\"correct-horse-battery\"}"))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualToIgnoringCase(ErrorCode.INTERNAL_ERROR.name());
        assertThat(response.body()).doesNotContain("secret-value").doesNotContain("IllegalStateException");
    }
}
