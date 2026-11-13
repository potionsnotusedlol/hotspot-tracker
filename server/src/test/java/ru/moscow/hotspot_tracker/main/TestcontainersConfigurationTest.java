package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks that the shared containers match the stand: PostGIS, the two database roles and Redis. */
class TestcontainersConfigurationTest extends AbstractIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void postgisAndTrigramExtensionsAreAvailable() {
        String postgis = jdbc.queryForObject("SELECT postgis_version()", String.class);
        Integer trigram =
                jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);

        assertThat(postgis).isNotBlank();
        assertThat(trigram).isEqualTo(1);
    }

    @Test
    void applicationConnectsAsTheApplicationRoleWithoutDdlRights() {
        String user = jdbc.queryForObject("SELECT current_user", String.class);

        assertThat(user).isEqualTo(TestcontainersConfiguration.appUser());
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE forbidden_table (id integer)"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    void applicationRoleSeesTablesCreatedByTheMigrationRole() throws SQLException {
        try (Connection migration = DriverManager.getConnection(
                        TestcontainersConfiguration.jdbcUrl(),
                        TestcontainersConfiguration.migrationUser(),
                        TestcontainersConfiguration.migrationPassword());
                Statement statement = migration.createStatement()) {
            statement.execute("CREATE TABLE role_check (id integer PRIMARY KEY)");
            try {
                jdbc.update("INSERT INTO role_check (id) VALUES (1)");

                assertThat(jdbc.queryForObject("SELECT count(*) FROM role_check", Integer.class))
                        .isEqualTo(1);
            } finally {
                statement.execute("DROP TABLE role_check");
            }
        }
    }

    @Test
    void redisAnswersFromTheContainer() {
        redis.opsForValue().set("integration-test:ping", "pong");

        assertThat(redis.opsForValue().get("integration-test:ping")).isEqualTo("pong");
        assertThat(redis.delete("integration-test:ping")).isTrue();
    }
}
