BEGIN;
SELECT pg_advisory_xact_lock(718034201);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version=4) THEN
        RAISE EXCEPTION 'Apply migration 004 first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version=5) THEN
        CREATE TABLE users.operation_permission (
            operation_id bigint NOT NULL REFERENCES users.api_operation(id) ON DELETE CASCADE,
            permission varchar(128) NOT NULL,
            PRIMARY KEY(operation_id, permission)
        );
        CREATE TABLE users.permission_role (
            permission varchar(128) NOT NULL,
            role_id integer NOT NULL REFERENCES users.role(id) ON DELETE CASCADE,
            PRIMARY KEY(permission, role_id)
        );
        CREATE TABLE users.policy_audit (
            id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            occurred_at timestamptz NOT NULL DEFAULT now(),
            actor text NOT NULL,
            action text NOT NULL,
            target text NOT NULL,
            reason text NOT NULL,
            before_state jsonb NOT NULL,
            after_state jsonb NOT NULL
        );
        INSERT INTO users.schema_migration(version,description) VALUES (5,'Permission mapping and policy administration audit');
    END IF;
END $$;
COMMIT;
