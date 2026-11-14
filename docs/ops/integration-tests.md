# Integration tests

Integration tests run against a real PostGIS and Redis started by Testcontainers. Unit tests do not need Docker.

## Commands

Run from `server/`:

| Command | What it runs | Docker |
|---|---|---|
| `./gradlew test` | unit tests (everything not tagged `integration`) | not needed |
| `./gradlew integrationTest` | tests tagged `integration` | needed |
| `./gradlew build` | analyzers, both test tasks, coverage rules (the same as CI) | needed |

Coverage (JaCoCo) is collected from both test tasks, so the rules for the service layer, `auth` and `load` see the integration tests too.

## Writing an integration test

Extend `AbstractIntegrationTest` (`server/src/test/java/ru/moscow/hotspot_tracker/main`):

```java
class AccessPointRepositoryTest extends AbstractIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findsPointsWithinRadius() {
        // ...
    }
}
```

The base class provides the full application context, the containers and the `integration` tag. To change the web environment or add properties, annotate the subclass (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@TestPropertySource`, ...).

A test that already has its own annotations can import the containers directly: `@Import(TestcontainersConfiguration.class)`. Tagged tests go to `integrationTest`; an untagged test with containers still works, but runs in `test` and needs Docker there. Prefer the base class.

## What the containers look like

- Images are the same as in `ops/compose/docker-compose.yml` (PostGIS 18-3.6 by digest, Redis 8.10.2). When you change one, change the other.
- The containers start once per test JVM and are shared by all Spring contexts, so a new context does not start new containers.
- The database is prepared by `ops/compose/initdb/20-roles.sh`, the same script as on the stand: the migration role owns the schema and runs Flyway, the application connects as the application role without DDL rights (NFR-SEC-05). A test that needs DDL has to go through a migration.
- The database is shared by all test classes: clean up what a test creates, or use unique values.

`./gradlew bootTestRun` starts the application with the same containers.

## Docker on Windows

Testcontainers needs a Docker daemon.

- **Docker Desktop:** nothing to configure.
- **Docker Engine in WSL:** expose the daemon on `127.0.0.1:2375` and put `docker.host=tcp://127.0.0.1:2375` into `~/.testcontainers.properties`. WSL stops when idle: run `wsl -d Ubuntu -e true` before the tests.

## Troubleshooting

- **`Could not find a valid Docker environment`:** Docker is not running or not reachable (see above). `./gradlew test` still works.
- **The first run is slow:** the images are pulled once. Later runs reuse them.
- **A port or container conflict:** Testcontainers picks random host ports, so it does not clash with the compose stand.
