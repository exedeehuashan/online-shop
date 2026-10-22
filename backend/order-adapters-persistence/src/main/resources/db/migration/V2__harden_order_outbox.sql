ALTER TABLE outbox_events ADD COLUMN aggregate_version BIGINT;
ALTER TABLE outbox_events ADD COLUMN correlation_id UUID;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM (
      SELECT aggregate_type, aggregate_public_id,
             count(*) FILTER (WHERE event_type = 'OrderCreated') AS created_count,
             min(event_type) FILTER (WHERE rn = 1) AS first_type
      FROM (
        SELECT e.*, row_number() OVER (
          PARTITION BY aggregate_type, aggregate_public_id ORDER BY occurred_at, internal_id
        ) AS rn
        FROM outbox_events e
      ) ordered
      GROUP BY aggregate_type, aggregate_public_id
    ) histories
    WHERE created_count <> 1 OR first_type <> 'OrderCreated'
  ) THEN
    RAISE EXCEPTION 'cannot backfill ambiguous outbox aggregate history';
  END IF;
END;
$$;

WITH versions AS (
  SELECT internal_id,
         row_number() OVER (PARTITION BY aggregate_type, aggregate_public_id ORDER BY occurred_at, internal_id) - 1 AS version
  FROM outbox_events
)
UPDATE outbox_events e SET aggregate_version = versions.version, correlation_id = e.event_id
FROM versions WHERE versions.internal_id = e.internal_id;

ALTER TABLE outbox_events ALTER COLUMN aggregate_version SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_aggregate_version CHECK (aggregate_version >= 0);
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_aggregate_type CHECK (aggregate_type = 'Order');
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object');
CREATE UNIQUE INDEX ux_outbox_aggregate_version
  ON outbox_events(aggregate_type, aggregate_public_id, aggregate_version);
DROP INDEX ix_outbox_aggregate_order;
CREATE INDEX ix_outbox_aggregate_order
  ON outbox_events(aggregate_type, aggregate_public_id, aggregate_version);

CREATE FUNCTION reject_outbox_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'outbox event history is immutable' USING ERRCODE = 'check_violation';
  END IF;
  IF ROW(NEW.event_id, NEW.aggregate_type, NEW.aggregate_public_id, NEW.aggregate_internal_id,
         NEW.aggregate_version, NEW.event_type, NEW.event_version, NEW.correlation_id,
         NEW.payload, NEW.occurred_at)
     IS DISTINCT FROM
     ROW(OLD.event_id, OLD.aggregate_type, OLD.aggregate_public_id, OLD.aggregate_internal_id,
         OLD.aggregate_version, OLD.event_type, OLD.event_version, OLD.correlation_id,
         OLD.payload, OLD.occurred_at) THEN
    RAISE EXCEPTION 'outbox event history is immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_outbox_immutable BEFORE UPDATE OR DELETE ON outbox_events
FOR EACH ROW EXECUTE FUNCTION reject_outbox_history_change();
