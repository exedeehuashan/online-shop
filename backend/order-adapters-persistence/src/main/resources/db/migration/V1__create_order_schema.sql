CREATE TABLE orders (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  public_id UUID NOT NULL,
  customer_ref VARCHAR(128) NOT NULL,
  customer_internal_id BIGINT NULL CHECK (customer_internal_id > 0),
  customer_name_snapshot VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
  currency CHAR(3) NOT NULL CHECK (currency = 'NZD'),
  total_amount NUMERIC(19,2) NOT NULL CHECK (total_amount >= 0),
  recipient VARCHAR(200) NOT NULL,
  address_line1 VARCHAR(200) NOT NULL,
  address_line2 VARCHAR(200),
  city VARCHAR(100) NOT NULL,
  region VARCHAR(100),
  postal_code VARCHAR(32) NOT NULL,
  country_code CHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
  phone VARCHAR(32),
  cancellation_reason VARCHAR(500),
  version BIGINT NOT NULL CHECK (version >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_orders_public_id ON orders(public_id);
CREATE INDEX ix_orders_customer_timeline ON orders(customer_ref, created_at DESC, internal_id DESC);

CREATE TABLE order_items (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  order_internal_id BIGINT NOT NULL REFERENCES orders(internal_id),
  product_ref VARCHAR(128) NOT NULL,
  product_internal_id BIGINT NULL CHECK (product_internal_id > 0),
  sku VARCHAR(100) NOT NULL,
  product_name_snapshot VARCHAR(300) NOT NULL,
  unit_amount_snapshot NUMERIC(19,2) NOT NULL CHECK (unit_amount_snapshot >= 0),
  currency CHAR(3) NOT NULL CHECK (currency = 'NZD'),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  line_total NUMERIC(19,2) NOT NULL CHECK (line_total >= 0),
  CONSTRAINT ux_order_items_order_sku UNIQUE(order_internal_id, sku)
);
CREATE INDEX ix_order_items_order ON order_items(order_internal_id);
CREATE INDEX ix_order_items_product_ref ON order_items(product_ref);

CREATE TABLE order_idempotency (
  customer_ref VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  order_internal_id BIGINT NOT NULL REFERENCES orders(internal_id),
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(customer_ref, idempotency_key)
);

CREATE TABLE outbox_events (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  event_id UUID NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_public_id UUID NOT NULL,
  aggregate_internal_id BIGINT NOT NULL,
  event_type VARCHAR(150) NOT NULL,
  event_version INTEGER NOT NULL CHECK (event_version > 0),
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);
CREATE UNIQUE INDEX ux_outbox_event_id ON outbox_events(event_id);
CREATE INDEX ix_outbox_aggregate_order ON outbox_events(aggregate_internal_id, occurred_at, internal_id);
CREATE INDEX ix_outbox_unpublished ON outbox_events(occurred_at, internal_id) WHERE published_at IS NULL;
