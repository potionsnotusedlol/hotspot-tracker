package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.moscow.hotspot_tracker.auth.PasswordHasher;
import ru.moscow.hotspot_tracker.common.error.ErrorCode;

/**
 * Tests the FR-AUTH-01 contract over HTTP against a real database.
 *
 * <p>Error codes are compared ignoring case: the contract describes them both as
 * {@code ErrorCode} constants and as lower-case values such as {@code email_taken}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AuthApiContractTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordHasher passwordHasher;

    private record Response(int status, String contentType, String body) {

        <T> T read(String path) {
            return JsonPath.read(body, path);
        }
    }

    private static String newEmail() {
        return "user%06d@example.com".formatted(COUNTER.incrementAndGet());
    }

    /** Builds a valid address of the exact length: a short local part and a long domain. */
    private static String emailOfLength(int length) {
        String local = "u%06d".formatted(COUNTER.incrementAndGet());
        int rest = length - local.length() - 1 - "com".length();
        StringBuilder domain = new StringBuilder();
        while (rest > 0) {
            int segment = Math.min(rest, 61);
            domain.append("a".repeat(segment - 1)).append('.');
            rest -= segment;
        }
        return local + "@" + domain + "com";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private Response post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(""), response.body());
    }

    private Response register(String email, String password) throws IOException, InterruptedException {
        return post("/api/v1/auth/register", credentials(email, password));
    }

    private Response login(String email, String password) throws IOException, InterruptedException {
        return post("/api/v1/auth/login", credentials(email, password));
    }

    private int usersWithEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private static void assertProblem(Response response, int status, ErrorCode code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.<Integer>read("$.status")).isEqualTo(status);
        assertThat(response.<String>read("$.code")).isEqualToIgnoringCase(code.name());
    }

    @Nested
    class Register {

        @Test
        void createsUserAndReturnsProfile() throws Exception {
            String email = newEmail();

            Response response = register(email, PASSWORD);

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.contentType()).startsWith("application/json");
            assertThat(UUID.fromString(response.read("$.id"))).isNotNull();
            assertThat(response.<String>read("$.email")).isEqualTo(email);
            assertThat(response.<String>read("$.role")).isEqualTo("USER");
        }

        @Test
        void responseDoesNotExposeThePasswordOrItsHash() throws Exception {
            String email = newEmail();

            Response response = register(email, PASSWORD);

            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
            assertThat(response.body()).doesNotContain(PASSWORD).doesNotContain(hash);
        }

        @Test
        void newUserIsStoredEnabledWithRoleUser() throws Exception {
            String email = newEmail();

            register(email, PASSWORD);

            assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE email = ?", String.class, email))
                    .isEqualTo("USER");
            assertThat(jdbc.queryForObject("SELECT enabled FROM app_user WHERE email = ?", Boolean.class, email))
                    .isTrue();
        }

        @Test
        void passwordIsStoredOnlyAsArgon2idHash() throws Exception {
            String email = newEmail();

            register(email, PASSWORD);

            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
            assertThat(hash).doesNotContain(PASSWORD).startsWith("$argon2id$");
        }

        @Test
        void takenEmailIsRejected() throws Exception {
            String email = newEmail();
            register(email, PASSWORD);

            assertProblem(register(email, "another-long-password"), 409, ErrorCode.EMAIL_TAKEN);
            assertThat(usersWithEmail(email)).isEqualTo(1);
        }

        @Test
        void concurrentRegistrationsCreateOnlyOneUser() throws Exception {
            String email = newEmail();
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> results = new ArrayList<>();

            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return register(email, PASSWORD);
                    }));
                }
                start.countDown();
            }

            List<Integer> statuses = new ArrayList<>();
            for (Future<Response> result : results) {
                statuses.add(getStatus(result));
            }
            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(usersWithEmail(email)).isEqualTo(1);
        }

        private int getStatus(Future<Response> result) throws InterruptedException, ExecutionException {
            return result.get().status();
        }

        @ParameterizedTest
        @ValueSource(strings = {"plainaddress", "user@", "@example.com", "a b@example.com", "", "   "})
        void invalidEmailIsRejected(String email) throws Exception {
            assertProblem(register(email, PASSWORD), 400, ErrorCode.VALIDATION_FAILED);
            assertThat(usersWithEmail(email)).isZero();
        }

        @Test
        void emailOfTheMaximumLengthIsAccepted() throws Exception {
            String email = emailOfLength(254);

            assertThat(email).hasSize(254);
            assertThat(register(email, PASSWORD).status()).isEqualTo(201);
        }

        @Test
        void emailOverTheMaximumLengthIsRejected() throws Exception {
            String email = emailOfLength(255);

            assertThat(email).hasSize(255);
            assertProblem(register(email, PASSWORD), 400, ErrorCode.VALIDATION_FAILED);
        }

        @ParameterizedTest
        @ValueSource(ints = {12, 128})
        void passwordAtTheLengthBoundsIsAccepted(int length) throws Exception {
            assertThat(register(newEmail(), "p".repeat(length)).status()).isEqualTo(201);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 11, 129})
        void passwordOutsideTheLengthBoundsIsRejected(int length) throws Exception {
            String email = newEmail();

            assertProblem(register(email, "p".repeat(length)), 400, ErrorCode.VALIDATION_FAILED);
            assertThat(usersWithEmail(email)).isZero();
        }

        @Test
        void missingEmailIsRejected() throws Exception {
            assertProblem(post("/api/v1/auth/register", "{\"password\":\"" + PASSWORD + "\"}"),
                    400, ErrorCode.VALIDATION_FAILED);
        }

        @Test
        void missingPasswordIsRejected() throws Exception {
            String email = newEmail();

            assertProblem(post("/api/v1/auth/register", "{\"email\":\"" + email + "\"}"),
                    400, ErrorCode.VALIDATION_FAILED);
            assertThat(usersWithEmail(email)).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {"{not json", "", "[1,2"})
        void unreadableBodyIsRejected(String body) throws Exception {
            assertProblem(post("/api/v1/auth/register", body), 400, ErrorCode.MALFORMED_BODY);
        }
    }

    @Nested
    class Login {

        @Test
        void returnsTheRegisteredProfile() throws Exception {
            String email = newEmail();
            Response registered = register(email, PASSWORD);

            Response response = login(email, PASSWORD);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.contentType()).startsWith("application/json");
            assertThat(response.<String>read("$.id")).isEqualTo(registered.<String>read("$.id"));
            assertThat(response.<String>read("$.email")).isEqualTo(email);
            assertThat(response.<String>read("$.role")).isEqualTo("USER");
            assertThat(response.body()).doesNotContain(PASSWORD);
        }

        @Test
        void returnsTheStoredRole() throws Exception {
            String email = newEmail();
            register(email, PASSWORD);
            jdbc.update("UPDATE app_user SET role = 'ANALYST' WHERE email = ?", email);

            assertThat(login(email, PASSWORD).<String>read("$.role")).isEqualTo("ANALYST");
        }

        @Test
        void wrongPasswordIsRejected() throws Exception {
            String email = newEmail();
            register(email, PASSWORD);

            assertProblem(login(email, "wrong-password-123"), 401, ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void unknownEmailIsRejectedLikeAWrongPassword() throws Exception {
            assertProblem(login(newEmail(), PASSWORD), 401, ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void disabledUserIsRejected() throws Exception {
            String email = newEmail();
            register(email, PASSWORD);
            jdbc.update("UPDATE app_user SET enabled = false WHERE email = ?", email);

            assertProblem(login(email, PASSWORD), 403, ErrorCode.USER_DISABLED);
        }

        @Test
        void unreadableBodyIsRejected() throws Exception {
            assertProblem(post("/api/v1/auth/login", "{not json"), 400, ErrorCode.MALFORMED_BODY);
        }
    }

    @Nested
    class Hasher {

        @Test
        void hashHidesThePassword() {
            assertThat(passwordHasher.hash(PASSWORD)).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        }

        @Test
        void hashMatchesOnlyItsPassword() {
            String hash = passwordHasher.hash(PASSWORD);

            assertThat(passwordHasher.matches(PASSWORD, hash)).isTrue();
            assertThat(passwordHasher.matches("another-password", hash)).isFalse();
        }

        @Test
        void hashFitsTheColumn() {
            assertThat(passwordHasher.hash("p".repeat(128))).hasSizeLessThanOrEqualTo(255);
        }
    }

    @Test
    void passwordsAndEmailsAreNotLogged(CapturedOutput output) throws Exception {
        String email = newEmail();

        register(email, PASSWORD);
        login(email, "wrong-password-123");
        login(email, PASSWORD);

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain("wrong-password-123")
                .doesNotContain(email);
    }
}
