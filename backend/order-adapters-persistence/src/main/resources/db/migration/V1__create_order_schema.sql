CREATE TABLE orders (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  public_id UUID NOT NULL CONSTRAINT ux_orders_public_id UNIQUE,
  customer_ref VARCHAR(128) NOT NULL CHECK (char_length(customer_ref) > 0),
  customer_internal_id BIGINT NULL CHECK (customer_internal_id > 0),
  customer_name_snapshot VARCHAR(200) NOT NULL CHECK (char_length(customer_name_snapshot) > 0),
  status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
  currency CHAR(3) NOT NULL CHECK (currency = 'NZD'),
  total_amount NUMERIC(19,2) NOT NULL CHECK (total_amount >= 0),
  item_count INTEGER NOT NULL CHECK (item_count > 0),
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
  updated_at TIMESTAMPTZ NOT NULL,
  CHECK (updated_at >= created_at),
  CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL))
);
ALTER TABLE orders ADD CONSTRAINT ux_orders_internal_public UNIQUE(internal_id, public_id);
CREATE INDEX ix_orders_customer_timeline ON orders(customer_ref, created_at DESC, internal_id DESC);

CREATE TABLE order_items (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  order_internal_id BIGINT NOT NULL REFERENCES orders(internal_id),
  product_ref VARCHAR(128) NOT NULL CHECK (char_length(product_ref) > 0),
  product_internal_id BIGINT NULL CHECK (product_internal_id > 0),
  sku VARCHAR(100) NOT NULL CHECK (char_length(sku) > 0),
  product_name_snapshot VARCHAR(300) NOT NULL CHECK (char_length(product_name_snapshot) > 0),
  unit_amount_snapshot NUMERIC(19,2) NOT NULL CHECK (unit_amount_snapshot >= 0),
  currency CHAR(3) NOT NULL CHECK (currency = 'NZD'),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  line_total NUMERIC(19,2) NOT NULL CHECK (line_total = unit_amount_snapshot * quantity),
  CONSTRAINT ux_order_items_order_sku UNIQUE(order_internal_id, sku)
);
CREATE INDEX ix_order_items_order ON order_items(order_internal_id);
CREATE INDEX ix_order_items_product_ref ON order_items(product_ref);

CREATE TABLE order_idempotency (
  customer_ref VARCHAR(128) NOT NULL CHECK (char_length(customer_ref) > 0),
  idempotency_key VARCHAR(128) NOT NULL CHECK (char_length(idempotency_key) > 0),
  request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  order_internal_id BIGINT NOT NULL REFERENCES orders(internal_id),
  order_public_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(customer_ref, idempotency_key),
  FOREIGN KEY(order_internal_id, order_public_id) REFERENCES orders(internal_id, public_id)
);

CREATE TABLE outbox_events (
  internal_id BIGINT PRIMARY KEY CHECK (internal_id > 0),
  event_id UUID NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_public_id UUID NOT NULL,
  aggregate_internal_id BIGINT NOT NULL,
  event_type VARCHAR(150) NOT NULL CHECK (char_length(event_type) > 0),
  event_version INTEGER NOT NULL CHECK (event_version > 0),
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  CHECK (published_at IS NULL OR published_at >= occurred_at)
);
CREATE UNIQUE INDEX ux_outbox_event_id ON outbox_events(event_id);
CREATE INDEX ix_outbox_aggregate_order ON outbox_events(aggregate_internal_id, occurred_at, internal_id);
CREATE INDEX ix_outbox_unpublished ON outbox_events(occurred_at, internal_id) WHERE published_at IS NULL;

CREATE FUNCTION reject_order_snapshot_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF ROW(NEW.public_id, NEW.customer_ref, NEW.customer_internal_id, NEW.customer_name_snapshot,
         NEW.currency, NEW.total_amount, NEW.item_count, NEW.recipient, NEW.address_line1, NEW.address_line2,
         NEW.city, NEW.region, NEW.postal_code, NEW.country_code, NEW.phone, NEW.created_at)
     IS DISTINCT FROM
     ROW(OLD.public_id, OLD.customer_ref, OLD.customer_internal_id, OLD.customer_name_snapshot,
         OLD.currency, OLD.total_amount, OLD.item_count, OLD.recipient, OLD.address_line1, OLD.address_line2,
         OLD.city, OLD.region, OLD.postal_code, OLD.country_code, OLD.phone, OLD.created_at) THEN
    RAISE EXCEPTION 'order snapshots are immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_orders_immutable_snapshot BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION reject_order_snapshot_change();

CREATE FUNCTION reject_order_item_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'order item snapshots are immutable' USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_order_items_immutable BEFORE UPDATE OR DELETE ON order_items
FOR EACH ROW EXECUTE FUNCTION reject_order_item_change();

CREATE FUNCTION reject_extra_order_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected_count INTEGER;
DECLARE existing_count INTEGER;
BEGIN
  SELECT item_count INTO expected_count FROM orders WHERE internal_id = NEW.order_internal_id FOR UPDATE;
  SELECT count(*) INTO existing_count FROM order_items WHERE order_internal_id = NEW.order_internal_id;
  IF existing_count >= expected_count THEN
    RAISE EXCEPTION 'order item set is immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_order_items_no_extra BEFORE INSERT ON order_items
FOR EACH ROW EXECUTE FUNCTION reject_extra_order_item();
