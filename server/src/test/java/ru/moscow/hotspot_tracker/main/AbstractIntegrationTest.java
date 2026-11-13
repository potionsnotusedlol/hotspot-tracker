package ru.moscow.hotspot_tracker.main;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for tests that need the real PostGIS and Redis (Testcontainers).
 *
 * <p>The subclass gets the full application context, the migrations applied by Flyway and the shared
 * containers from {@link TestcontainersConfiguration}. The {@code integration} tag moves the test out of
 * {@code ./gradlew test} (fast, no Docker) into {@code ./gradlew integrationTest}; {@code ./gradlew build} runs both.
 *
 * <p>Tests must not depend on each other's data: the database is shared by all test classes, so clean up what a
 * test creates or use unique values. Web tests can override the environment, for example
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} on the subclass.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {}
