-- Stop the three research services and connect to adaptive_commerce first.
BEGIN;
DO $guard$
BEGIN
    IF current_database() <> 'adaptive_commerce' THEN
        RAISE EXCEPTION 'Reset is allowed only in adaptive_commerce';
    END IF;
END
$guard$;
-- List the known tables explicitly. Dependencies outside this stand cause a rollback.
DROP TABLE IF EXISTS orders.line, orders.purchase;
DROP TABLE IF EXISTS inventory.reservation_item, inventory.reservation, inventory.stock;
DROP TABLE IF EXISTS catalog.product, catalog.category;
COMMIT;
