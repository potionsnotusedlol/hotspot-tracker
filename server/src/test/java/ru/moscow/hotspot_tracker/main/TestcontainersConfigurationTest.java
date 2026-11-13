package ru.moscow.hotspot_tracker.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks that the shared containers match the stand: PostGIS, the two database roles and Redis. */
class TestcontainersConfigurationTest extends AbstractIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

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
}
