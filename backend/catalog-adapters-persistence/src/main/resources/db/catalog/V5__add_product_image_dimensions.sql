ALTER TABLE product_image
  ADD COLUMN width_px integer,
  ADD COLUMN height_px integer,
  ADD CONSTRAINT ck_product_image_dimensions CHECK (
    (width_px IS NULL AND height_px IS NULL) OR
    (width_px IS NOT NULL AND height_px IS NOT NULL AND
     width_px BETWEEN 1 AND 32768 AND height_px BETWEEN 1 AND 32768)
  );
