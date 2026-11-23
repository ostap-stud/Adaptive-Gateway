-- Disposable DB only; all fixtures are rolled back.
BEGIN;
DO $$
DECLARE s integer; op bigint; policy bigint;
BEGIN
    ASSERT EXISTS (SELECT 1 FROM users.schema_migration WHERE version = 3);
    INSERT INTO users.service(service_name) VALUES ('policy-sql-check') RETURNING id INTO s;
    INSERT INTO users.api_operation(service_id, backend_path, gateway_path, http_method)
        VALUES (s, '/items', '/policy/items', 'GET') RETURNING id INTO op;
    BEGIN
        INSERT INTO users.api_operation(service_id, backend_path, gateway_path, http_method)
            VALUES (s, '/items', '/policy/items', 'GET');
        RAISE EXCEPTION 'Duplicate operation accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    INSERT INTO users.access_policy(api_operation_id, source, access_type)
        VALUES (op, 'OPENAPI', 'PUBLIC') RETURNING id INTO policy;
    BEGIN
        UPDATE users.access_policy SET force_override = true WHERE id = policy;
        RAISE EXCEPTION 'Non-admin force override accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO users.access_policy_role(policy_id, role_id) VALUES (policy, 2147483647);
        RAISE EXCEPTION 'Unknown role accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    DELETE FROM users.api_operation WHERE id = op;
    ASSERT NOT EXISTS (SELECT 1 FROM users.access_policy WHERE id = policy);
END $$;
ROLLBACK;
