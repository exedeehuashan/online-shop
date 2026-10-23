-- Variant signatures are lossless canonical attribute/value UUID pairs. Their size grows
-- with the number of variant axes, so a fixed-width column is not a safe domain boundary.
ALTER TABLE product_sku
    ALTER COLUMN variant_signature TYPE text;
