CREATE SCHEMA IF NOT EXISTS orders;
CREATE TABLE IF NOT EXISTS orders.purchase (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    status varchar(16) NOT NULL CHECK(status IN ('PENDING','RESERVED','CONFIRMED','CANCELLING','CANCELLED'))
);
CREATE TABLE IF NOT EXISTS orders.line (
    order_id uuid NOT NULL REFERENCES orders.purchase,
    product_id bigint NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(14,2) NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY(order_id, product_id)
);
