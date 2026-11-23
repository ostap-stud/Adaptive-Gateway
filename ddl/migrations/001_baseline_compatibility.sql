-- Run with psql -v ON_ERROR_STOP=1, after restoring ddl/users.sql.
-- Repeatable: the version row and all schema changes commit together.
BEGIN;
SELECT pg_advisory_xact_lock(718034201);

CREATE TABLE IF NOT EXISTS users.schema_migration (
    version integer PRIMARY KEY,
    description text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 1) THEN
        ALTER TABLE users.route ALTER COLUMN service_id DROP NOT NULL;
        ALTER TABLE users.route ADD CONSTRAINT route_external_service_required
            CHECK (is_internal OR service_id IS NOT NULL);
        UPDATE users.route SET "order" = 0 WHERE "order" IS NULL;
        ALTER TABLE users.route ALTER COLUMN "order" SET DEFAULT 0;
        ALTER TABLE users.route ALTER COLUMN "order" SET NOT NULL;
        INSERT INTO users.schema_migration(version, description)
            VALUES (1, 'Internal routes and non-null route priority');
    END IF;
END $$;
COMMIT;
