CREATE TABLE products (
 id uuid PRIMARY KEY,
 name varchar(160) NOT NULL CHECK (length(trim(name)) > 0),
 description varchar(2000) NOT NULL,
 price numeric(19,2) NOT NULL CHECK (price >= 0),
 currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
 stock_quantity integer NOT NULL CHECK (stock_quantity >= 0),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL
);
CREATE INDEX products_created_at_id_idx ON products (created_at DESC, id DESC);
