#!/bin/sh
# Creates two database roles (NFR-SEC-05):
#   - migration role: owns the schema and runs Flyway migrations (DDL allowed);
#   - application role: data access only (DML), no DDL.
# Runs once, on the first start with an empty data volume, after the image's 10_postgis.sh.
#
# No `set -eu` here: if the file loses its executable bit (e.g. checked out on Windows),
# the entrypoint sources it instead of running it, and shell options would leak into the entrypoint.
# Errors still fail the init: psql stops on the first error and is the last command.

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v db_name="$POSTGRES_DB" \
  -v migration_user="$DB_MIGRATION_USER" \
  -v migration_password="$DB_MIGRATION_PASSWORD" \
  -v app_user="$DB_APP_USER" \
  -v app_password="$DB_APP_PASSWORD" <<'SQL'
CREATE ROLE :"migration_user" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';

-- PostGIS requires a superuser, so extensions are created here.
-- Migrations can still declare them with CREATE EXTENSION IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO :"migration_user", :"app_user";

ALTER SCHEMA public OWNER TO :"migration_user";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app_user";

-- Objects created by the migration role become available to the application role.
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO :"app_user";
SQL
