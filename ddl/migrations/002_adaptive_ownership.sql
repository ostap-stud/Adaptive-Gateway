BEGIN;
SELECT pg_advisory_xact_lock(718034201);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 1) THEN
        RAISE EXCEPTION 'Apply migration 001 first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 2) THEN
        IF EXISTS (SELECT service_name FROM users.service GROUP BY service_name HAVING count(*) > 1) THEN
            RAISE EXCEPTION 'Resolve duplicate service_name values before migration 002';
        END IF;
        ALTER TABLE users.service
            ADD COLUMN management_mode varchar(16) NOT NULL DEFAULT 'MANUAL'
                CHECK (management_mode IN ('MANUAL', 'AUTOMATIC', 'DISABLED')),
            ADD COLUMN discovery_status varchar(16) NOT NULL DEFAULT 'UNKNOWN'
                CHECK (discovery_status IN ('UNKNOWN', 'AVAILABLE', 'UNAVAILABLE')),
            ADD COLUMN openapi_path text,
            ADD COLUMN openapi_hash text,
            ADD COLUMN last_seen_at timestamptz,
            ADD COLUMN last_synced_at timestamptz,
            ADD COLUMN configuration_version bigint NOT NULL DEFAULT 0,
            ADD CONSTRAINT service_name_unique UNIQUE (service_name);
        ALTER TABLE users.route
            ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANUAL'
                CHECK (source IN ('MANUAL', 'GENERATED')),
            ADD COLUMN managed_key text,
            ADD COLUMN active boolean NOT NULL DEFAULT true,
            ADD COLUMN is_overridden boolean NOT NULL DEFAULT false,
            ADD CONSTRAINT generated_route_identity_required
                CHECK (source <> 'GENERATED' OR (service_id IS NOT NULL AND managed_key IS NOT NULL AND length(trim(managed_key)) > 0));
        CREATE UNIQUE INDEX route_managed_key_unique ON users.route(service_id, managed_key)
            WHERE managed_key IS NOT NULL;
        INSERT INTO users.schema_migration(version, description)
            VALUES (2, 'Manual and generated configuration ownership');
    END IF;
END $$;
COMMIT;
