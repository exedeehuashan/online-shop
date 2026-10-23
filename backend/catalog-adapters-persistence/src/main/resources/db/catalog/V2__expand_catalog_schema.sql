CREATE TABLE product (
    id uuid CONSTRAINT pk_product PRIMARY KEY,
    legacy_id uuid,
    slug varchar(180) NOT NULL,
    name varchar(160) NOT NULL,
    description text NOT NULL,
    status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_legacy_id UNIQUE (legacy_id),
    CONSTRAINT ck_product_slug_nonblank CHECK (length(trim(slug)) > 0),
    CONSTRAINT ck_product_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_product_slug_normalized ON product (lower(slug));
CREATE INDEX idx_product_status_updated ON product (status, updated_at DESC, id);

CREATE TABLE product_attribute (
    id uuid CONSTRAINT pk_product_attribute PRIMARY KEY,
    code varchar(80) NOT NULL,
    name varchar(120) NOT NULL,
    data_type varchar(16) NOT NULL,
    scope varchar(16) NOT NULL,
    is_variant_axis boolean NOT NULL DEFAULT false,
    is_filterable boolean NOT NULL DEFAULT false,
    is_required boolean NOT NULL DEFAULT false,
    validation_json jsonb,
    status varchar(24) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_attribute_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_product_attribute_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_product_attribute_data_type CHECK (data_type IN ('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN')),
    CONSTRAINT ck_product_attribute_scope CHECK (scope IN ('PRODUCT', 'SKU', 'BOTH')),
    CONSTRAINT ck_product_attribute_validation_object CHECK (validation_json IS NULL OR jsonb_typeof(validation_json) = 'object'),
    CONSTRAINT ck_product_attribute_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_attribute_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_product_attribute_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_attribute_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_product_attribute_code_normalized ON product_attribute (lower(code));
CREATE UNIQUE INDEX uq_product_attribute_id_data_type ON product_attribute (id, data_type);

CREATE TABLE product_attribute_value (
    id uuid CONSTRAINT pk_product_attribute_value PRIMARY KEY,
    attribute_id uuid NOT NULL,
    attribute_data_type varchar(16) NOT NULL,
    text_value text,
    integer_value bigint,
    decimal_value numeric(38, 10),
    boolean_value boolean,
    display_value varchar(240),
    display_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_attribute_value_attribute FOREIGN KEY (attribute_id, attribute_data_type)
        REFERENCES product_attribute (id, data_type) ON DELETE RESTRICT,
    CONSTRAINT uq_product_attribute_value_id_attribute UNIQUE (id, attribute_id),
    CONSTRAINT ck_product_attribute_value_typed CHECK (
        (attribute_data_type = 'TEXT' AND text_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NULL) OR
        (attribute_data_type = 'INTEGER' AND text_value IS NULL AND integer_value IS NOT NULL AND decimal_value IS NULL AND boolean_value IS NULL) OR
        (attribute_data_type = 'DECIMAL' AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NOT NULL AND boolean_value IS NULL) OR
        (attribute_data_type = 'BOOLEAN' AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL AND boolean_value IS NOT NULL)
    ),
    CONSTRAINT ck_product_attribute_value_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_product_attribute_value_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_attribute_value_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_product_attribute_value_text ON product_attribute_value (attribute_id, text_value) WHERE text_value IS NOT NULL;
CREATE UNIQUE INDEX uq_product_attribute_value_integer ON product_attribute_value (attribute_id, integer_value) WHERE integer_value IS NOT NULL;
CREATE UNIQUE INDEX uq_product_attribute_value_decimal ON product_attribute_value (attribute_id, decimal_value) WHERE decimal_value IS NOT NULL;
CREATE UNIQUE INDEX uq_product_attribute_value_boolean ON product_attribute_value (attribute_id, boolean_value) WHERE boolean_value IS NOT NULL;

CREATE FUNCTION prevent_valued_product_attribute_identity_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.code <> OLD.code OR NEW.data_type <> OLD.data_type)
       AND EXISTS (SELECT 1 FROM product_attribute_value WHERE attribute_id = OLD.id) THEN
        RAISE EXCEPTION 'attribute code and data type are immutable after values exist';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_product_attribute_identity_immutable
BEFORE UPDATE OF code, data_type ON product_attribute
FOR EACH ROW EXECUTE FUNCTION prevent_valued_product_attribute_identity_change();

CREATE TABLE product_attribute_assignment (
    product_id uuid NOT NULL,
    attribute_id uuid NOT NULL,
    value_id uuid NOT NULL,
    CONSTRAINT pk_product_attribute_assignment PRIMARY KEY (product_id, attribute_id, value_id),
    CONSTRAINT fk_product_attribute_assignment_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_attribute_assignment_value FOREIGN KEY (value_id, attribute_id)
        REFERENCES product_attribute_value (id, attribute_id) ON DELETE RESTRICT
);

CREATE TABLE product_sku (
    id uuid CONSTRAINT pk_product_sku PRIMARY KEY,
    product_id uuid NOT NULL,
    sku varchar(100) NOT NULL,
    status varchar(24) NOT NULL,
    price_amount numeric(19, 2) NOT NULL,
    currency char(3) NOT NULL,
    available_quantity integer NOT NULL,
    barcode varchar(100),
    is_default boolean NOT NULL DEFAULT false,
    variant_signature varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_sku_product_id_id UNIQUE (product_id, id),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_sku_sku_nonblank CHECK (length(trim(sku)) > 0),
    CONSTRAINT ck_product_sku_normalized CHECK (sku = upper(sku)),
    CONSTRAINT ck_product_sku_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_product_sku_price CHECK (price_amount >= 0),
    CONSTRAINT ck_product_sku_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_product_sku_quantity CHECK (available_quantity >= 0),
    CONSTRAINT ck_product_sku_variant_signature CHECK (length(trim(variant_signature)) > 0),
    CONSTRAINT ck_product_sku_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_sku_version CHECK (version >= 0),
    CONSTRAINT uq_product_sku_variant_signature UNIQUE (product_id, variant_signature)
);
CREATE UNIQUE INDEX uq_product_sku_sku_normalized ON product_sku (upper(sku));
CREATE UNIQUE INDEX uq_product_sku_default ON product_sku (product_id) WHERE is_default;
CREATE INDEX idx_product_sku_product_status ON product_sku (product_id, status);
CREATE INDEX idx_product_sku_status_updated ON product_sku (status, updated_at, id);

CREATE TABLE product_sku_attribute_value (
    product_sku_id uuid NOT NULL,
    attribute_id uuid NOT NULL,
    value_id uuid NOT NULL,
    CONSTRAINT pk_product_sku_attribute_value PRIMARY KEY (product_sku_id, attribute_id, value_id),
    CONSTRAINT uq_product_sku_attribute_axis UNIQUE (product_sku_id, attribute_id),
    CONSTRAINT fk_product_sku_attribute_sku FOREIGN KEY (product_sku_id) REFERENCES product_sku (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_sku_attribute_value FOREIGN KEY (value_id, attribute_id)
        REFERENCES product_attribute_value (id, attribute_id) ON DELETE RESTRICT
);

CREATE TABLE category (
    id uuid CONSTRAINT pk_category PRIMARY KEY,
    slug varchar(180) NOT NULL,
    name varchar(160) NOT NULL,
    description text NOT NULL DEFAULT '',
    parent_id uuid,
    status varchar(24) NOT NULL,
    display_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_category_slug_nonblank CHECK (length(trim(slug)) > 0),
    CONSTRAINT ck_category_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_category_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_category_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_category_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_category_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_category_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_category_slug_normalized ON category (lower(slug));
CREATE INDEX idx_category_parent ON category (parent_id);

CREATE TABLE product_category (
    product_id uuid NOT NULL,
    category_id uuid NOT NULL,
    CONSTRAINT pk_product_category PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_category_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_category_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT
);
CREATE INDEX idx_product_category_category ON product_category (category_id, product_id);

CREATE TABLE product_image (
    id uuid CONSTRAINT pk_product_image PRIMARY KEY,
    product_id uuid NOT NULL,
    product_sku_id uuid,
    url varchar(2048) NOT NULL,
    alt_text varchar(500) NOT NULL DEFAULT '',
    display_order integer NOT NULL DEFAULT 0,
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    created_by uuid,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_image_sku FOREIGN KEY (product_id, product_sku_id)
        REFERENCES product_sku (product_id, id) ON DELETE SET NULL (product_sku_id),
    CONSTRAINT ck_product_image_url CHECK (url ~ '^https://' OR (left(url, 1) = '/' AND left(url, 2) <> '//')),
    CONSTRAINT ck_product_image_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_product_image_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_image_version CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_product_image_primary_product ON product_image (product_id) WHERE is_primary AND product_sku_id IS NULL;
CREATE UNIQUE INDEX uq_product_image_primary_sku ON product_image (product_sku_id) WHERE is_primary AND product_sku_id IS NOT NULL;
CREATE INDEX idx_product_image_product_order ON product_image (product_id, display_order, id);

CREATE FUNCTION demote_primary_images_before_product_sku_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE product_image
       SET is_primary = false,
           updated_at = statement_timestamp(),
           version = version + 1
     WHERE product_sku_id = OLD.id
       AND is_primary;
    RETURN OLD;
END;
$$;
CREATE TRIGGER trg_product_sku_demote_primary_images
BEFORE DELETE ON product_sku
FOR EACH ROW EXECUTE FUNCTION demote_primary_images_before_product_sku_delete();

CREATE TABLE catalog_legacy_migration_issue (
    legacy_product_id uuid NOT NULL,
    issue_code varchar(80) NOT NULL,
    issue_detail text NOT NULL,
    detected_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    CONSTRAINT pk_catalog_legacy_migration_issue PRIMARY KEY (legacy_product_id, issue_code)
);

CREATE TABLE catalog_legacy_reconciliation (
    id smallint CONSTRAINT pk_catalog_legacy_reconciliation PRIMARY KEY DEFAULT 1,
    status varchar(16) NOT NULL,
    legacy_count bigint NOT NULL,
    product_count bigint NOT NULL,
    sku_count bigint NOT NULL,
    legacy_checksum char(32) NOT NULL,
    expanded_checksum char(32) NOT NULL,
    reconciled_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    CONSTRAINT ck_catalog_legacy_reconciliation_singleton CHECK (id = 1),
    CONSTRAINT ck_catalog_legacy_reconciliation_status CHECK (status IN ('READY', 'NOT_READY', 'MISMATCH'))
);

CREATE OR REPLACE PROCEDURE catalog_preflight_and_backfill_legacy_products(batch_size integer DEFAULT 500)
LANGUAGE plpgsql
AS $$
DECLARE
    last_id uuid;
    batch_ids uuid[];
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('catalog_preflight_and_backfill_legacy_products'));
    IF batch_size < 1 OR batch_size > 10000 THEN
        RAISE EXCEPTION 'batch_size must be between 1 and 10000';
    END IF;

    TRUNCATE catalog_legacy_migration_issue;
    INSERT INTO catalog_legacy_migration_issue (legacy_product_id, issue_code, issue_detail)
    SELECT id, 'UPDATED_BEFORE_CREATED', 'updated_at must not precede created_at'
      FROM products WHERE updated_at < created_at;

    IF EXISTS (SELECT 1 FROM catalog_legacy_migration_issue) THEN
        INSERT INTO catalog_legacy_reconciliation
            (id, status, legacy_count, product_count, sku_count, legacy_checksum, expanded_checksum, reconciled_at)
        WITH legacy AS (
            SELECT count(*) AS row_count,
                   -- JSON preserves field boundaries/nulls; UTF-8 hex makes row framing unambiguous.
                   md5(COALESCE(string_agg(
                       encode(convert_to(jsonb_build_array(id, name, description, price, currency, stock_quantity)::text, 'UTF8'), 'hex'),
                       E'\n' ORDER BY id), '')) AS checksum
              FROM products
        ), expanded AS (
            SELECT count(*) AS row_count,
                   md5(COALESCE(string_agg(
                       encode(convert_to(jsonb_build_array(p.legacy_id, p.name, p.description, s.price_amount, s.currency, s.available_quantity)::text, 'UTF8'), 'hex'),
                       E'\n' ORDER BY p.legacy_id), '')) AS checksum
              FROM product p JOIN product_sku s ON s.product_id = p.id AND s.is_default
             WHERE p.legacy_id IS NOT NULL
        ), sku AS (
            SELECT count(*) AS row_count FROM product_sku
        )
        SELECT 1, 'NOT_READY', legacy.row_count, expanded.row_count, sku.row_count,
               legacy.checksum, expanded.checksum, statement_timestamp()
          FROM legacy CROSS JOIN expanded CROSS JOIN sku
        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, legacy_count = EXCLUDED.legacy_count,
            product_count = EXCLUDED.product_count, sku_count = EXCLUDED.sku_count,
            legacy_checksum = EXCLUDED.legacy_checksum, expanded_checksum = EXCLUDED.expanded_checksum,
            reconciled_at = EXCLUDED.reconciled_at;
        RETURN;
    END IF;

    LOOP
        SELECT array_agg(id ORDER BY id) INTO batch_ids
          FROM (SELECT id FROM products WHERE last_id IS NULL OR id > last_id ORDER BY id LIMIT batch_size) next_batch;
        EXIT WHEN batch_ids IS NULL;

        INSERT INTO product (id, legacy_id, slug, name, description, status, created_at, updated_at)
        SELECT id, id, 'legacy-' || replace(id::text, '-', ''), name, description, 'ACTIVE', created_at, updated_at
          FROM products WHERE id = ANY(batch_ids)
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO product_sku
            (id, product_id, sku, status, price_amount, currency, available_quantity, is_default,
             variant_signature, created_at, updated_at)
        SELECT id, id, 'LEGACY-' || upper(replace(id::text, '-', '')), 'ACTIVE', price, currency,
               stock_quantity, true, 'legacy-default', created_at, updated_at
          FROM products WHERE id = ANY(batch_ids)
        ON CONFLICT (id) DO NOTHING;

        last_id := batch_ids[array_length(batch_ids, 1)];
    END LOOP;

    INSERT INTO catalog_legacy_reconciliation
        (id, status, legacy_count, product_count, sku_count, legacy_checksum, expanded_checksum, reconciled_at)
    WITH legacy AS (
        SELECT count(*) AS row_count,
               -- Apply the identical canonical UTF-8 representation to both reconciliation sides.
               md5(COALESCE(string_agg(
                   encode(convert_to(jsonb_build_array(id, name, description, price, currency, stock_quantity)::text, 'UTF8'), 'hex'),
                   E'\n' ORDER BY id), '')) AS checksum
          FROM products
    ), expanded AS (
        SELECT count(*) AS row_count,
               md5(COALESCE(string_agg(
                   encode(convert_to(jsonb_build_array(p.legacy_id, p.name, p.description, s.price_amount, s.currency, s.available_quantity)::text, 'UTF8'), 'hex'),
                   E'\n' ORDER BY p.legacy_id), '')) AS checksum
          FROM product p JOIN product_sku s ON s.product_id = p.id AND s.is_default
         WHERE p.legacy_id IS NOT NULL
    ), sku AS (
        SELECT count(*) AS row_count FROM product_sku
    )
    SELECT 1,
           CASE WHEN legacy.row_count = expanded.row_count AND legacy.checksum = expanded.checksum THEN 'READY' ELSE 'MISMATCH' END,
           legacy.row_count, expanded.row_count, sku.row_count, legacy.checksum, expanded.checksum,
           statement_timestamp()
      FROM legacy CROSS JOIN expanded CROSS JOIN sku
    ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, legacy_count = EXCLUDED.legacy_count,
        product_count = EXCLUDED.product_count, sku_count = EXCLUDED.sku_count,
        legacy_checksum = EXCLUDED.legacy_checksum, expanded_checksum = EXCLUDED.expanded_checksum,
        reconciled_at = EXCLUDED.reconciled_at;
END;
$$;
