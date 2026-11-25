CREATE SCHEMA IF NOT EXISTS inventory;
CREATE TABLE IF NOT EXISTS inventory.stock (
    product_id bigint PRIMARY KEY CHECK (product_id > 0),
    on_hand integer NOT NULL CHECK (on_hand >= 0),
    reserved integer NOT NULL DEFAULT 0 CHECK (reserved BETWEEN 0 AND on_hand)
);
CREATE TABLE IF NOT EXISTS inventory.reservation (
    order_id uuid PRIMARY KEY,
    released boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS inventory.reservation_item (
    order_id uuid NOT NULL REFERENCES inventory.reservation,
    product_id bigint NOT NULL REFERENCES inventory.stock,
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY(order_id, product_id)
);
INSERT INTO inventory.stock(product_id,on_hand) VALUES
 (1,40),(2,25),(3,18),(4,10),(5,50),(6,6) ON CONFLICT DO NOTHING;
