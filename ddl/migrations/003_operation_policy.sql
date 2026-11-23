BEGIN;
SELECT pg_advisory_xact_lock(718034201);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 2) THEN
        RAISE EXCEPTION 'Apply migration 002 first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 3) THEN
        CREATE TABLE users.api_operation (
            id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            service_id integer NOT NULL REFERENCES users.service(id) ON DELETE CASCADE,
            backend_path text NOT NULL CHECK (backend_path LIKE '/%'),
            gateway_path text NOT NULL CHECK (gateway_path LIKE '/%'),
            http_method varchar(10) NOT NULL CHECK (http_method IN ('GET','HEAD','POST','PUT','DELETE','PATCH','OPTIONS','TRACE')),
            operation_id text,
            active boolean NOT NULL DEFAULT true,
            spec_hash text,
            UNIQUE(service_id, gateway_path, http_method)
        );
        CREATE TABLE users.access_policy (
            id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            api_operation_id bigint NOT NULL REFERENCES users.api_operation(id) ON DELETE CASCADE,
            source varchar(16) NOT NULL CHECK (source IN ('DEFAULT','OPENAPI','CENTRAL','ADMIN')),
            access_type varchar(16) NOT NULL CHECK (access_type IN ('PUBLIC','AUTHENTICATED','ROLES','BLOCKED')),
            force_override boolean NOT NULL DEFAULT false CHECK (NOT force_override OR source = 'ADMIN'),
            reason text,
            UNIQUE(api_operation_id, source)
        );
        CREATE TABLE users.access_policy_role (
            policy_id bigint NOT NULL REFERENCES users.access_policy(id) ON DELETE CASCADE,
            role_id integer NOT NULL REFERENCES users.role(id),
            PRIMARY KEY (policy_id, role_id)
        );
        INSERT INTO users.schema_migration(version, description)
            VALUES (3, 'Operation access policies with separate policy layers');
    END IF;
END $$;
COMMIT;
