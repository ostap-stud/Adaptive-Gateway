BEGIN;
SELECT pg_advisory_xact_lock(718034201);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 3) THEN
        RAISE EXCEPTION 'Apply migration 003 first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 4) THEN
        ALTER TABLE users.api_operation ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANUAL'
            CHECK (source IN ('MANUAL', 'GENERATED'));
        CREATE TABLE users.adaptation_event (
            id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            service_id integer NOT NULL REFERENCES users.service(id) ON DELETE CASCADE,
            request_id uuid NOT NULL,
            input_hash varchar(64) NOT NULL,
            configuration_version bigint NOT NULL,
            detected_at timestamptz NOT NULL DEFAULT now(),
            persisted_at timestamptz NOT NULL DEFAULT now(),
            applied_at timestamptz,
            status varchar(32) NOT NULL CHECK (status IN
                ('NO_CHANGE','PENDING_REFRESH','FAILED_REFRESH','APPLIED','SUPERSEDED')),
            counts jsonb NOT NULL,
            expected_route_ids jsonb NOT NULL,
            duration_ms bigint NOT NULL,
            error text,
            UNIQUE(service_id, request_id)
        );
        CREATE INDEX adaptation_event_pending ON users.adaptation_event(status)
            WHERE status IN ('PENDING_REFRESH','FAILED_REFRESH');
        INSERT INTO users.schema_migration(version, description)
            VALUES (4, 'Generated operation ownership and durable adaptation events');
    END IF;
END $$;
COMMIT;
