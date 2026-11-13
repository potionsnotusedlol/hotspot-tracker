package ru.moscow.hotspot_tracker.main;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostGIS and Redis for integration tests.
 *
 * <p>The containers are started once per test JVM and shared by every Spring context, so a new context
 * (a different {@code @MockitoBean} set or properties) does not start new containers. Testcontainers stops
 * them when the JVM exits.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    // The same images as in ops/compose/docker-compose.yml
    private static final String POSTGIS_IMAGE =
            "postgis/postgis:18-3.6@sha256:60f6ad1d21ea86a67d47780b9a0d1e1d200500f62b19293fa834d0dea80b8677";
    private static final String REDIS_IMAGE = "redis:8.10.2-alpine";
    private static final int REDIS_PORT = 6379;

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse(POSTGIS_IMAGE).asCompatibleSubstituteFor("postgres"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);

    static {
        Startables.deepStart(POSTGRES, REDIS).join();
    }

    /** Points the application at the containers. */
    @Bean
    DynamicPropertyRegistrar containerProperties() {
        return registry -> {
            registry.add("spring.datasource.url", TestcontainersConfiguration::jdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", TestcontainersConfiguration::redisPort);
        };
    }

    /** JDBC URL of the shared database. */
    static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    /** Host port mapped to the shared Redis. */
    static int redisPort() {
        return REDIS.getMappedPort(REDIS_PORT);
    }
}
