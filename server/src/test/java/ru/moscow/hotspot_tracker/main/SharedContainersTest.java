package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

/**
 * A second Spring context (different properties) must use the same containers as the first one,
 * not start its own.
 */
@TestPropertySource(properties = "hotspot.test.second-context=true")
class SharedContainersTest extends AbstractIntegrationTest {
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Test
    void anotherContextConnectsToTheSharedDatabase() {
        assertThat(datasourceUrl).isEqualTo(TestcontainersConfiguration.jdbcUrl());
    }

    @Test
    void anotherContextConnectsToTheSharedRedis() {
        assertThat(redisPort).isEqualTo(TestcontainersConfiguration.redisPort());
    }
}
