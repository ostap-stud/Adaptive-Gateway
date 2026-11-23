-- Run against a disposable restored database after migrations 001 and 002.
BEGIN;
DO $$
DECLARE test_service integer; test_route integer;
BEGIN
    ASSERT (SELECT count(*) FROM users.schema_migration WHERE version IN (1,2)) = 2;
    ASSERT NOT EXISTS (SELECT 1 FROM users.route WHERE "order" IS NULL);
    INSERT INTO users.service(service_name) VALUES ('sql-baseline-test') RETURNING id INTO test_service;
    ASSERT (SELECT management_mode = 'MANUAL' FROM users.service WHERE id = test_service);
    INSERT INTO users.route(route, is_internal) VALUES ('/sql-test/**', true) RETURNING id INTO test_route;
    ASSERT (SELECT service_id IS NULL AND "order" = 0 AND active AND source = 'MANUAL'
            FROM users.route WHERE id = test_route);
    BEGIN
        INSERT INTO users.route(route, is_internal) VALUES ('/invalid-external/**', false);
        RAISE EXCEPTION 'External route without service was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    INSERT INTO users.route(route, service_id, source, managed_key)
        VALUES ('/generated/**', test_service, 'GENERATED', 'main');
    BEGIN
        INSERT INTO users.route(route, service_id, source, managed_key)
            VALUES ('/duplicate/**', test_service, 'GENERATED', 'main');
        RAISE EXCEPTION 'Duplicate managed key was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END $$;
ROLLBACK;
