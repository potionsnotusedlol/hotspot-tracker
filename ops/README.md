# ops

Infrastructure for local development, CI and testing.

## Local environment: PostgreSQL + PostGIS and Redis

| Service | Image | Host address |
|---|---|---|
| PostgreSQL 18 + PostGIS 3.6 | `postgis/postgis:18-3.6` (pinned by digest) | `localhost:5433` |
| Redis 8.10 | `redis:8.10.2-alpine` | `localhost:6379` |

Ports are bound to `127.0.0.1` only. PostgreSQL uses 5433 so it does not clash with a locally installed PostgreSQL on 5432.

### Requirements

Docker with Compose v2, one of:

- **Linux:** Docker Engine with the Compose plugin.
- **macOS:** Docker Desktop.
- **Windows:** Docker Desktop, or Docker Engine with the Compose plugin installed inside a WSL 2 distribution (no Docker Desktop).

### First-time setup

Create `.env` from the example and set real passwords **before the first start**: the database roles are created only once, on an empty data volume.

| Shell | Command (from the repository root) |
|---|---|
| Linux, macOS, WSL | `cp ops/compose/.env.example ops/compose/.env` |
| Windows cmd | `copy ops\compose\.env.example ops\compose\.env` |
| Windows PowerShell | `Copy-Item ops/compose/.env.example ops/compose/.env` |

### Start

All commands are run from the repository root.

**Linux, macOS, Windows with Docker Desktop** (any terminal; Docker Desktop must be running):

```sh
docker compose -f ops/compose/docker-compose.yml up -d
docker compose -f ops/compose/docker-compose.yml ps   # both services must be "healthy"
```

**Windows with Docker Engine in WSL 2** (from cmd or PowerShell):

```cmd
wsl -d Ubuntu -e docker compose -f ops/compose/docker-compose.yml up -d
wsl -d Ubuntu -e docker compose -f ops/compose/docker-compose.yml ps
```

- `wsl` starts in the same directory, translated to `/mnt/<drive>/...`.
- Replace `Ubuntu` with your distribution name (`wsl -l -v`), or drop `-d Ubuntu` if it is the default one.
- `-e` passes the arguments to Docker as is. Without it they go through the Linux shell, and quotes or `;` in SQL may break.
- Alternatively, open the WSL shell, `cd` to the repository and use the plain `docker compose` commands.
- The ports are reachable from Windows at `localhost`.

In the commands below, WSL users prefix `docker compose ...` with `wsl -d Ubuntu -e`.

### Check

```sh
docker compose -f ops/compose/docker-compose.yml exec postgres psql -U postgres -d hotspot -c "SELECT postgis_full_version();"
docker compose -f ops/compose/docker-compose.yml exec redis redis-cli ping
```

`-U postgres` and `-d hotspot` are the defaults of `POSTGRES_SUPERUSER` and `POSTGRES_DB`; use your values if you changed them.

### Connect

- JDBC URL: `jdbc:postgresql://localhost:5433/hotspot`
- Flyway migrations: `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD`. This role owns the `public` schema and may run DDL.
- Application at runtime: `DB_APP_USER` / `DB_APP_PASSWORD`. This role can only read and modify data (no DDL); it gets access to every table the migration role creates.
- Redis: `localhost:6379`, no password (local development only).

The roles and the `postgis` and `pg_trgm` extensions are created by `compose/initdb/20-roles.sh`.

### API mock server

Prism serves the example responses from `docs/openapi.yaml`, so clients can be built before the backend is ready. It needs the same `.env` file and a non-empty `docs/openapi.yaml`. It only starts with the `mock` profile:

```sh
docker compose -f ops/compose/docker-compose.yml --profile mock up -d mock
curl "http://localhost:4010/api/v1/access-points?page=0&size=2"
docker compose -f ops/compose/docker-compose.yml --profile mock rm -sf mock
```

- Android emulator: use `http://10.0.2.2:4010`.
- After changing `docs/openapi.yaml`, recreate the container (`up -d --force-recreate mock`).
- Answers are static examples: there is no real data, login or permission check. Use the backend for anything that depends on them.
- Contract problems found while serving a response are printed in the log: `docker compose -f ops/compose/docker-compose.yml --profile mock logs mock`.

### API server

The `app` profile builds the server image from `server/Dockerfile` and starts it next to PostgreSQL and Redis. The image uses Eclipse Temurin 25 (JRE only, layered jar), runs as a non-root user and has a healthcheck on `/actuator/health`. It needs the same `.env` file.

```sh
docker compose -f ops/compose/docker-compose.yml --profile app up -d --build app
docker compose -f ops/compose/docker-compose.yml --profile app ps    # app must be "healthy"
curl http://localhost:8080/actuator/health
```

- The application connects as `DB_APP_USER` (no DDL); Flyway migrations run as `DB_MIGRATION_USER`.
- After code changes run `up -d --build app` again. The first build downloads Gradle and the dependencies and takes a few minutes; later builds reuse the Docker cache.
- The host port is `APP_PORT` (default 8080).
- If the build cannot download Gradle on a network without IPv6, pass `--build-arg GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` to `docker build` (or set it under `build.args` in the compose file for a local run).
- On `master` the image is published to GitHub Container Registry as `ghcr.io/potionsnotusedlol/hotspot-tracker/server`, tagged `sha-<commit>` and `master`.

### Stop and reset

```sh
docker compose -f ops/compose/docker-compose.yml down      # stop, keep data
docker compose -f ops/compose/docker-compose.yml down -v   # stop and delete all data
```

The init script runs only on the first start with an empty data volume. After changing it, or the roles and passwords in `.env`, recreate the database with `down -v`.

### Troubleshooting

- **`address already in use` / a service stays in `Created`:** the host port is taken (often by a locally installed PostgreSQL or Redis). Change `POSTGRES_PORT`, `REDIS_PORT` or `MOCK_PORT` in `.env` and run `up -d` again.
- **Mac with Apple Silicon:** the PostGIS and Prism images are published for amd64 only, so they run under emulation (Docker Desktop prints a platform warning). They work, just slower; keep "Use Rosetta for x86_64/amd64 emulation" enabled in Docker Desktop settings.
- **Image pull fails or hangs:** Docker Hub may be slow or unreachable from some networks. Configure a registry mirror in the Docker daemon settings (`registry-mirrors` in `/etc/docker/daemon.json` or in Docker Desktop settings) and retry.
