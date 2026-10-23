package com.example.onlineshop.catalog.api;

import org.junit.jupiter.api.Tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("docker") @Testcontainers(disabledWithoutDocker = false)
class CatalogMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.6-alpine");

    @BeforeAll
    static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
    }

    @Test
    void freshInstallValidatesApprovedTablesConstraintsAndIndexes() throws Exception {
        Database database = database("fresh");
        database.flyway().migrate();
        database.flyway().validate();

        assertThat(database.queryLong("SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name IN "
                + "('products','product','product_sku','product_attribute','product_attribute_value','product_attribute_assignment',"
                + "'product_sku_attribute_value','category','product_category','product_image')"))
                .isEqualTo(10);
        assertThat(database.queryLong("SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN "
                + "('uq_product_slug_normalized','uq_product_sku_default','uq_product_sku_variant_signature',"
                + "'uq_product_image_primary_product','uq_product_image_primary_sku')"))
                .isEqualTo(5);
        assertThat(database.queryLong("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() "
                + "AND table_name='products' AND column_name IN "
                + "('id','name','description','price','currency','stock_quantity','created_at','updated_at')"))
                .isEqualTo(8);
        assertThat(database.queryLong("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace "
                + "AND contype='f' AND confdeltype='a'"))
                .isZero();

        UUID attribute = UUID.randomUUID();
        UUID value = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        database.execute("INSERT INTO product_attribute "
                + "(id,code,name,data_type,scope,status,created_at,updated_at) VALUES ('" + attribute
                + "','colour','Colour','TEXT','BOTH','ACTIVE','" + now + "','" + now + "')");
        assertThatThrownBy(() -> database.execute("INSERT INTO product_attribute_value "
                + "(id,attribute_id,attribute_data_type,integer_value,created_at,updated_at) VALUES ('" + value + "','"
                + attribute + "','TEXT',1,'" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("INSERT INTO product_attribute "
                + "(id,code,name,data_type,scope,status,created_at,updated_at) VALUES ('" + UUID.randomUUID()
                + "','COLOUR','Duplicate','TEXT','PRODUCT','ACTIVE','" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        database.execute("INSERT INTO product_attribute_value "
                + "(id,attribute_id,attribute_data_type,text_value,created_at,updated_at) VALUES ('" + value + "','"
                + attribute + "','TEXT','green','" + now + "','" + now + "')");
        assertThatThrownBy(() -> database.execute("UPDATE product_attribute SET code='shade' WHERE id='" + attribute + "'"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void schemaEnforcesNormalizedKeysCardinalityDeletionAndLifecycleRules() throws Exception {
        Database database = database("constraints");
        database.flyway().migrate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UUID product = UUID.randomUUID();
        UUID otherProduct = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID otherSku = UUID.randomUUID();
        database.execute("INSERT INTO product (id,slug,name,description,status,created_at,updated_at) VALUES "
                + "('" + product + "','tea','Tea','','ACTIVE','" + now + "','" + now + "'),"
                + "('" + otherProduct + "','coffee','Coffee','','ACTIVE','" + now + "','" + now + "')");
        database.execute("INSERT INTO product_sku "
                + "(id,product_id,sku,status,price_amount,currency,available_quantity,is_default,variant_signature,created_at,updated_at) VALUES "
                + "('" + sku + "','" + product + "','TEA-1','ACTIVE',1.00,'NZD',1,true,'default','" + now + "','" + now + "')");

        assertThatThrownBy(() -> database.execute("INSERT INTO product (id,slug,name,description,status,created_at,updated_at) VALUES "
                + "('" + UUID.randomUUID() + "','TEA','Duplicate','','ACTIVE','" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("INSERT INTO product_sku "
                + "(id,product_id,sku,status,price_amount,currency,available_quantity,is_default,variant_signature,created_at,updated_at) VALUES "
                + "('" + otherSku + "','" + product + "','TEA-2','ACTIVE',1.00,'NZD',1,true,'other','" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("INSERT INTO product_sku "
                + "(id,product_id,sku,status,price_amount,currency,available_quantity,is_default,variant_signature,created_at,updated_at) VALUES "
                + "('" + otherSku + "','" + product + "','tea-2','ACTIVE',1.00,'NZD',1,false,'other','" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("DELETE FROM product WHERE id='" + product + "'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("UPDATE product_sku SET version=-1 WHERE id='" + sku + "'"))
                .isInstanceOf(SQLException.class);

        database.execute("INSERT INTO product_image "
                + "(id,product_id,product_sku_id,url,is_primary,created_at,updated_at) VALUES "
                + "('" + UUID.randomUUID() + "','" + product + "','" + sku + "','/tea.png',true,'" + now + "','" + now + "')");
        assertThatThrownBy(() -> database.execute("INSERT INTO product_image "
                + "(id,product_id,product_sku_id,url,is_primary,created_at,updated_at) VALUES "
                + "('" + UUID.randomUUID() + "','" + product + "','" + sku + "','https://example.test/tea.png',true,'" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> database.execute("INSERT INTO product_image "
                + "(id,product_id,product_sku_id,url,created_at,updated_at) VALUES "
                + "('" + UUID.randomUUID() + "','" + otherProduct + "','" + sku + "','/wrong-owner.png','" + now + "','" + now + "')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void deletingSkuDemotesItsPrimaryImageBeforeMovingItToProductScope() throws Exception {
        Database database = database("image_delete");
        database.flyway().migrate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UUID product = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID productImage = UUID.randomUUID();
        UUID skuImage = UUID.randomUUID();
        database.execute("INSERT INTO product (id,slug,name,description,status,created_at,updated_at) VALUES "
                + "('" + product + "','image-delete','Image delete','','ACTIVE','" + now + "','" + now + "')");
        database.execute("INSERT INTO product_sku "
                + "(id,product_id,sku,status,price_amount,currency,available_quantity,is_default,variant_signature,created_at,updated_at) VALUES "
                + "('" + sku + "','" + product + "','IMAGE-DELETE','ACTIVE',1.00,'NZD',1,true,'default','" + now + "','" + now + "')");
        database.execute("INSERT INTO product_image "
                + "(id,product_id,product_sku_id,url,is_primary,created_at,updated_at) VALUES "
                + "('" + productImage + "','" + product + "',NULL,'/product.png',true,'" + now + "','" + now + "'),"
                + "('" + skuImage + "','" + product + "','" + sku + "','/sku.png',true,'" + now + "','" + now + "')");

        database.execute("DELETE FROM product_sku WHERE id='" + sku + "'");

        assertThat(database.queryLong("SELECT count(*) FROM product_image WHERE id='" + skuImage
                + "' AND product_sku_id IS NULL AND NOT is_primary AND version=1")).isEqualTo(1);
        assertThat(database.queryLong("SELECT count(*) FROM product_image WHERE id='" + productImage
                + "' AND product_sku_id IS NULL AND is_primary")).isEqualTo(1);
    }

    @Test
    void exactBaselineUpgradeBackfillsInBatchesAndIsRestartable() throws Exception {
        Database database = database("upgrade");
        database.flyway("1").migrate();
        UUID zero = new UUID(0, 0);
        UUID first = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("20000000-0000-0000-0000-000000000002");
        insertLegacy(database, zero, "Water", "Still", "1.00", "NZD", 9);
        insertLegacy(database, first, "Tea", "Leaves", "12.30", "NZD", 4);
        insertLegacy(database, second, "Coffee", "Beans", "9.50", "NZD", 7);

        database.flyway().migrate();
        database.flyway().validate();
        assertThat(database.queryLong("SELECT count(*) FROM products")).isEqualTo(3);
        assertThat(database.queryLong("SELECT count(*) FROM product WHERE id = legacy_id")).isEqualTo(3);
        assertThat(database.queryLong("SELECT count(*) FROM product_sku WHERE id = product_id AND is_default")).isEqualTo(3);
        assertThat(database.queryString("SELECT status FROM catalog_legacy_reconciliation WHERE id=1")).isEqualTo("READY");
        assertThat(database.queryString("SELECT sku FROM product_sku WHERE id='" + first + "'"))
                .isEqualTo("LEGACY-10000000000000000000000000000001");

        String checksum = database.queryString("SELECT expanded_checksum FROM catalog_legacy_reconciliation WHERE id=1");
        database.execute("CALL catalog_preflight_and_backfill_legacy_products(1)");
        assertThat(database.queryLong("SELECT count(*) FROM product")).isEqualTo(3);
        assertThat(database.queryLong("SELECT count(*) FROM product_sku")).isEqualTo(3);
        assertThat(database.queryString("SELECT expanded_checksum FROM catalog_legacy_reconciliation WHERE id=1"))
                .isEqualTo(checksum);

        // The exact baseline contract remains usable after expansion.
        database.execute("UPDATE products SET stock_quantity=5, updated_at='2026-01-02T00:00:00Z' WHERE id='" + first + "'");
        assertThat(database.queryLong("SELECT stock_quantity FROM products WHERE id='" + first + "'"))
                .isEqualTo(5);
    }

    @Test
    void invalidLegacyDataIsReportedBeforeAnyBackfillAndCanBeReconciledAfterCorrection() throws Exception {
        Database database = database("invalid");
        database.flyway("1").migrate();
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000003");
        database.execute("INSERT INTO products (id,name,description,price,currency,stock_quantity,created_at,updated_at) VALUES "
                + "('" + id + "','Invalid','Fixture',1.00,'NZD',1,'2026-01-02T00:00:00Z','2026-01-01T00:00:00Z')");
        database.flyway("2").migrate();

        database.execute("CALL catalog_preflight_and_backfill_legacy_products(1)");
        assertThat(database.queryString("SELECT issue_code FROM catalog_legacy_migration_issue WHERE legacy_product_id='" + id + "'"))
                .isEqualTo("UPDATED_BEFORE_CREATED");
        assertThat(database.queryString("SELECT status FROM catalog_legacy_reconciliation WHERE id=1")).isEqualTo("NOT_READY");
        assertThat(database.queryLong("SELECT count(*) FROM product")).isZero();

        database.execute("UPDATE products SET updated_at=created_at WHERE id='" + id + "'");
        database.execute("CALL catalog_preflight_and_backfill_legacy_products(1)");
        assertThat(database.queryLong("SELECT count(*) FROM catalog_legacy_migration_issue")).isZero();
        assertThat(database.queryString("SELECT status FROM catalog_legacy_reconciliation WHERE id=1")).isEqualTo("READY");
    }

    @Test
    void reconciliationChecksumRejectsDelimiterShiftAndReportsAccurateMetadata() throws Exception {
        Database database = database("reconciliation");
        database.flyway("1").migrate();
        UUID valid = UUID.fromString("40000000-0000-0000-0000-000000000004");
        insertLegacy(database, valid, "Tea|Green", "Leaves", "12.30", "NZD", 4);
        database.flyway().migrate();

        database.execute("UPDATE product SET name='Tea', description='Green|Leaves' WHERE legacy_id='" + valid + "'");
        database.execute("CALL catalog_preflight_and_backfill_legacy_products(1)");
        assertThat(database.queryString("SELECT status FROM catalog_legacy_reconciliation WHERE id=1"))
                .isEqualTo("MISMATCH");
        assertThat(database.queryLong("SELECT (legacy_checksum <> expanded_checksum)::int "
                + "FROM catalog_legacy_reconciliation WHERE id=1")).isEqualTo(1);

        database.execute("UPDATE product SET name='Tea|Green', description='Leaves' WHERE legacy_id='" + valid + "'");
        UUID extraSku = UUID.randomUUID();
        database.execute("INSERT INTO product_sku "
                + "(id,product_id,sku,status,price_amount,currency,available_quantity,is_default,variant_signature,created_at,updated_at) VALUES "
                + "('" + extraSku + "','" + valid + "','EXTRA-SKU','ACTIVE',2.00,'NZD',2,false,'extra',"
                + "'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
        UUID invalid = UUID.fromString("50000000-0000-0000-0000-000000000005");
        database.execute("INSERT INTO products (id,name,description,price,currency,stock_quantity,created_at,updated_at) VALUES "
                + "('" + invalid + "','Invalid','Fixture',1.00,'NZD',1,'2026-01-02T00:00:00Z','2026-01-01T00:00:00Z')");
        database.execute("CALL catalog_preflight_and_backfill_legacy_products(1)");

        assertThat(database.queryString("SELECT status FROM catalog_legacy_reconciliation WHERE id=1"))
                .isEqualTo("NOT_READY");
        assertThat(database.queryLong("SELECT product_count FROM catalog_legacy_reconciliation WHERE id=1"))
                .isEqualTo(1);
        assertThat(database.queryLong("SELECT sku_count FROM catalog_legacy_reconciliation WHERE id=1"))
                .isEqualTo(2);
        assertThat(database.queryLong("SELECT (expanded_checksum <> md5(''))::int "
                + "FROM catalog_legacy_reconciliation WHERE id=1")).isEqualTo(1);
    }

    private static void insertLegacy(Database database, UUID id, String name, String description,
                                     String price, String currency, int quantity) throws SQLException {
        database.execute("INSERT INTO products (id,name,description,price,currency,stock_quantity,created_at,updated_at) VALUES "
                + "('" + id + "','" + name + "','" + description + "'," + price + ",'" + currency + "'," + quantity
                + ",'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
    }

    private static Database database(String prefix) throws SQLException {
        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        String separator = postgres.getJdbcUrl().contains("?") ? "&" : "?";
        return new Database(postgres.getJdbcUrl() + separator + "currentSchema=" + schema);
    }

    private record Database(String url) {
        Flyway flyway() {
            return Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/catalog").load();
        }

        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/catalog").target(target).load();
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        long queryLong(String sql) throws SQLException {
            return Long.parseLong(queryString(sql));
        }

        String queryString(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery(sql)) {
                results.next();
                return results.getString(1);
            }
        }
    }
}
