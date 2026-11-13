package ru.moscow.hotspot_tracker.main;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostGIS and Redis for integration tests.
 *
 * <p>The containers are started once per test JVM and shared by every Spring context, so a new context
 * (a different {@code @MockitoBean} set or properties) does not start new containers. Testcontainers stops
 * them when the JVM exits.
 *
 * <p>The database is prepared exactly like the local stand: the init script from {@code ops/compose/initdb}
 * creates the migration role (owns the schema, runs Flyway) and the application role (data access only,
 * NFR-SEC-05), so the tests see the same permissions as production.
 *
 * <p>Use it through {@link AbstractIntegrationTest}, or import it into a test directly.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    // The same images as in ops/compose/docker-compose.yml
    private static final String POSTGIS_IMAGE =
            "postgis/postgis:18-3.6@sha256:60f6ad1d21ea86a67d47780b9a0d1e1d200500f62b19293fa834d0dea80b8677";
    private static final String REDIS_IMAGE = "redis:8.10.2-alpine";
    private static final int REDIS_PORT = 6379;

    private static final String DATABASE = "hotspot";
    private static final String MIGRATION_USER = "hotspot_migrator";
    private static final String MIGRATION_PASSWORD = "migrator-test-secret";
    private static final String APP_USER = "hotspot_app";
    private static final String APP_PASSWORD = "app-test-secret";

    private static final String ROLES_SCRIPT = "ops/compose/initdb/20-roles.sh";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse(POSTGIS_IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DATABASE)
            .withEnv("DB_MIGRATION_USER", MIGRATION_USER)
            .withEnv("DB_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
            .withEnv("DB_APP_USER", APP_USER)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            // Copied, not bind-mounted, so it also works when Docker runs in WSL or on a remote host
            .withCopyFileToContainer(
                    MountableFile.forHostPath(findRolesScript(), 0755), "/docker-entrypoint-initdb.d/20-roles.sh");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);

    static {
        Startables.deepStart(POSTGRES, REDIS).join();
    }

    /**
     * Points the application at the containers. The application connects as the application role and
     * Flyway as the migration role, as on the stand.
     */
    @Bean
    DynamicPropertyRegistrar containerProperties() {
        return registry -> {
            registry.add("spring.datasource.url", TestcontainersConfiguration::jdbcUrl);
            registry.add("spring.datasource.username", () -> APP_USER);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
            registry.add("spring.flyway.url", TestcontainersConfiguration::jdbcUrl);
            registry.add("spring.flyway.user", () -> MIGRATION_USER);
            registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", TestcontainersConfiguration::redisPort);
        };
    }

    /** Host port mapped to the shared Redis. */
    static int redisPort() {
        return REDIS.getMappedPort(REDIS_PORT);
    }

    /** JDBC URL of the shared database. */
    static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    /** Migration role: owns the schema and may run DDL. */
    static String migrationUser() {
        return MIGRATION_USER;
    }

    static String migrationPassword() {
        return MIGRATION_PASSWORD;
    }

    /** Application role: data access only. */
    static String appUser() {
        return APP_USER;
    }

    // The working directory is the Gradle module (server/) or the repository root, depending on how the tests run
    private static Path findRolesScript() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(ROLES_SCRIPT);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                ROLES_SCRIPT + " not found above " + Path.of("").toAbsolutePath());
    }
}
