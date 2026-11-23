-- Development seed only. Adds a dedicated Actuator role and grants it to the
-- existing login supplied with: psql -v admin_login=myadmin -v ON_ERROR_STOP=1 ...
-- Does not change passwords or reuse the historical dump's credentials.
BEGIN;
SELECT pg_advisory_xact_lock(718034201);
CREATE TEMP TABLE baseline_admin ON COMMIT DROP AS
    SELECT id FROM users.user_login WHERE login = :'admin_login';
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM baseline_admin) THEN
        RAISE EXCEPTION 'The supplied admin_login does not exist';
    END IF;
    IF EXISTS (SELECT 1 FROM users.route WHERE route = '/actuator/**') THEN
        RAISE EXCEPTION 'An Actuator rule already exists; inspect it instead of changing its permissions automatically';
    END IF;
END $$;
INSERT INTO users.role(application, role_name, role_desc)
VALUES ('GATEWAY', 'ADMIN', 'Gateway management access')
ON CONFLICT (role_name, application) DO NOTHING;
INSERT INTO users.route(route, route_desc, service_id, is_internal, "order")
VALUES ('/actuator/**', 'Gateway management endpoints', NULL, true, 0);
INSERT INTO users.route_role(route_id, role_id)
SELECT r.id, p.id FROM users.route r CROSS JOIN users.role p
WHERE r.route = '/actuator/**' AND p.role = 'ROLE_GATEWAY_ADMIN';
INSERT INTO users.user_role(user_id, role_id)
SELECT a.id, p.id FROM baseline_admin a CROSS JOIN users.role p
WHERE p.role = 'ROLE_GATEWAY_ADMIN'
AND NOT EXISTS (SELECT 1 FROM users.user_role ur WHERE ur.user_id = a.id AND ur.role_id = p.id);
COMMIT;
