package com.example.onlineshop.order.persistence;

import org.junit.jupiter.api.Tag;
import static org.assertj.core.api.Assertions.*;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("docker") @Testcontainers
class PostgresPersistenceTest {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
  static DataSource dataSource;
  JdbcTemplate jdbc;
  PostgresOrderRepository orders;
  PostgresOutboxRepository outbox;

  @BeforeAll static void migrate() {
    PGSimpleDataSource source = new PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl()); source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
    dataSource = source;
    Flyway.configure().dataSource(source).load().migrate();
  }

  @BeforeEach void setup() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update("truncate outbox_events, order_idempotency, order_items, orders");
    orders = new PostgresOrderRepository(jdbc);
    outbox = new PostgresOutboxRepository(jdbc, new java.util.concurrent.atomic.AtomicLong(1000)::incrementAndGet, UUID::randomUUID);
  }

  @Test void migratesApprovedSchemaWithConstraintsAndIndexes() {
    assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
    assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname='public'", String.class))
        .contains("ux_orders_public_id", "ix_orders_customer_timeline", "ix_order_items_product_ref", "ix_outbox_aggregate_order");
    assertThatThrownBy(() -> jdbc.update("insert into orders(internal_id) values (1)"));
    Order order = sample(5, "05050505-0505-0505-0505-050505050505", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(order);
    assertThatThrownBy(() -> jdbc.update("""
        insert into order_items(internal_id,order_internal_id,product_ref,sku,product_name_snapshot,
          unit_amount_snapshot,currency,quantity,line_total) values (8,5,'product-3','SKU-3','Cable',10,'NZD',2,20)"""));
    assertThatThrownBy(() -> jdbc.update("""
        insert into order_items(internal_id,order_internal_id,product_ref,sku,product_name_snapshot,
          unit_amount_snapshot,currency,quantity,line_total) values (7,5,'product-2','SKU-2','Case',10,'NZD',2,19)"""));
    assertThatThrownBy(() -> jdbc.update("""
        insert into order_idempotency(customer_ref,idempotency_key,request_hash,order_internal_id,order_public_id,created_at)
        values ('customer-1','invalid-hash',?,5,?,'2026-10-22T00:00:00Z')""",
        "A".repeat(64), order.id().value()));
    Order another = sample(15, "15151515-1515-1515-1515-151515151515", Instant.parse("2026-10-22T00:00:01Z"));
    orders.save(another);
    assertThatThrownBy(() -> jdbc.update("""
        insert into order_idempotency(customer_ref,idempotency_key,request_hash,order_internal_id,order_public_id,created_at)
        values ('customer-1','mismatched-order',?,5,?,?)""",
        "a".repeat(64), another.id().value(), OffsetDateTime.ofInstant(another.createdAt(), ZoneOffset.UTC)));
  }

  @Test void hibernateMappingsValidateAgainstFlywaySchema() {
    assertThatCode(() -> PersistenceMappings.validate(dataSource)).doesNotThrowAnyException();
  }

  @Test void migratesValidV1HistoryWithDeterministicBackfill() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    PGSimpleDataSource source = schemaDataSource(schema);
    Flyway.configure().dataSource(source).schemas(schema).createSchemas(true).target("1").load().migrate();
    JdbcTemplate old = new JdbcTemplate(source);
    UUID aggregate = UUID.randomUUID();
    UUID createdId = UUID.randomUUID();
    UUID confirmedId = UUID.randomUUID();
    old.update("""
        insert into outbox_events(internal_id,event_id,aggregate_type,aggregate_public_id,aggregate_internal_id,
          event_type,event_version,payload,occurred_at) values (1,?,'Order',?,10,'OrderCreated',1,'{}',?),
          (2,?,'Order',?,10,'OrderConfirmed',1,'{}',?)""",
        createdId, aggregate, OffsetDateTime.parse("2026-10-22T00:00:00Z"),
        confirmedId, aggregate, OffsetDateTime.parse("2026-10-22T00:00:00Z"));
    Flyway.configure().dataSource(source).schemas(schema).load().migrate();
    assertThat(old.queryForList("select aggregate_version from outbox_events order by aggregate_version", Long.class))
        .containsExactly(0L, 1L);
    assertThat(old.queryForObject("select correlation_id from outbox_events where aggregate_version=0", UUID.class))
        .isEqualTo(createdId);
  }

  @Test void rejectsAmbiguousV1HistoryDuringMigration() {
    String schema = "ambiguous_" + UUID.randomUUID().toString().replace("-", "");
    PGSimpleDataSource source = schemaDataSource(schema);
    Flyway.configure().dataSource(source).schemas(schema).createSchemas(true).target("1").load().migrate();
    JdbcTemplate old = new JdbcTemplate(source);
    UUID aggregate = UUID.randomUUID();
    old.update("""
        insert into outbox_events(internal_id,event_id,aggregate_type,aggregate_public_id,aggregate_internal_id,
          event_type,event_version,payload,occurred_at) values (1,gen_random_uuid(),'Order',?,10,'OrderConfirmed',1,'{}',now())""", aggregate);
    assertThatThrownBy(() -> Flyway.configure().dataSource(source).schemas(schema).load().migrate())
        .hasMessageContaining("ambiguous outbox aggregate history");
  }

  @Test void roundTripsImmutableSnapshotsAndPagesNewestFirst() {
    Order older = sample(10, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", Instant.parse("2026-10-22T00:00:00Z"));
    Order newer = sample(20, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", Instant.parse("2026-10-22T00:01:00Z"));
    orders.save(older); orders.save(newer);
    Order loaded = orders.findByIdAndCustomer(older.id(), older.customerReference()).orElseThrow();
    assertThat(loaded).usingRecursiveComparison().ignoringFields("domainEvents").isEqualTo(older);
    assertThat(loaded.domainEvents()).isEmpty();
    assertThat(orders.findByCustomer(older.customerReference(), 0, 1)).extracting(Order::id).containsExactly(newer.id());
    assertThat(orders.findByCustomer(older.customerReference(), 1, 1)).extracting(Order::id).containsExactly(older.id());
  }

  @Test void staleMutationFailsOptimisticLocking() {
    Order original = sample(30, "cccccccc-cccc-cccc-cccc-cccccccccccc", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(original);
    Order first = orders.findByIdAndCustomer(original.id(), original.customerReference()).orElseThrow();
    Order stale = orders.findByIdAndCustomer(original.id(), original.customerReference()).orElseThrow();
    first.confirm(Instant.parse("2026-10-22T00:01:00Z")); orders.save(first);
    stale.cancel("changed mind", Instant.parse("2026-10-22T00:02:00Z"));
    assertThatThrownBy(() -> orders.save(stale)).isInstanceOf(OptimisticOrderLockException.class);

    Order multiMutation = sample(31, "31313131-3131-3131-3131-313131313131", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(multiMutation);
    Order loaded = orders.findByIdAndCustomer(multiMutation.id(), multiMutation.customerReference()).orElseThrow();
    loaded.confirm(Instant.parse("2026-10-22T00:01:00Z"));
    loaded.cancel("changed mind", Instant.parse("2026-10-22T00:02:00Z"));
    assertThatCode(() -> orders.save(loaded)).doesNotThrowAnyException();
    Order unchanged = orders.findByIdAndCustomer(multiMutation.id(), multiMutation.customerReference()).orElseThrow();
    assertThatCode(() -> orders.save(unchanged)).doesNotThrowAnyException();
  }

  @Test void persistedSnapshotsCannotBeChangedByAggregateMutationOrDirectSql() {
    Order original = sample(35, "abababab-abab-abab-abab-abababababab", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(original);
    Map<String, Object> orderSnapshot = jdbc.queryForMap("""
        select customer_ref,customer_name_snapshot,recipient,address_line1,address_line2,city,region,postal_code,country_code,phone
        from orders where internal_id=35""");
    Map<String, Object> itemSnapshot = jdbc.queryForMap("""
        select product_ref,product_internal_id,sku,product_name_snapshot,unit_amount_snapshot,currency,quantity,line_total
        from order_items where order_internal_id=35""");

    original.confirm(Instant.parse("2026-10-22T00:01:00Z"));
    orders.save(original);

    assertThat(jdbc.queryForMap("""
        select customer_ref,customer_name_snapshot,recipient,address_line1,address_line2,city,region,postal_code,country_code,phone
        from orders where internal_id=35""")).isEqualTo(orderSnapshot);
    assertThat(jdbc.queryForMap("""
        select product_ref,product_internal_id,sku,product_name_snapshot,unit_amount_snapshot,currency,quantity,line_total
        from order_items where order_internal_id=35""")).isEqualTo(itemSnapshot);
    assertThatThrownBy(() -> jdbc.update("update orders set customer_name_snapshot='Grace' where internal_id=35"));
    assertThatThrownBy(() -> jdbc.update("update order_items set unit_amount_snapshot=1 where order_internal_id=35"));
  }

  @Test void orderMutationAndOutboxInsertRollbackTogether() {
    Order order = sample(40, "dddddddd-dddd-dddd-dddd-dddddddddddd", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(order); order.clearDomainEvents();
    TransactionRunner tx = new SpringTransactionRunner(new DataSourceTransactionManager(dataSource));
    assertThatThrownBy(() -> tx.execute(() -> {
      order.confirm(Instant.parse("2026-10-22T00:01:00Z")); orders.save(order); outbox.appendAll(order.domainEvents());
      throw new IllegalStateException("rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(orders.findByIdAndCustomer(order.id(), order.customerReference()).orElseThrow().status()).isEqualTo(OrderStatus.DRAFT);
    assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isZero();
  }

  @Test void orderMutationAndOutboxInsertCommitTogether() {
    Order order = sample(45, "ffffffff-ffff-ffff-ffff-ffffffffffff", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(order); order.clearDomainEvents();
    TransactionRunner tx = new SpringTransactionRunner(new DataSourceTransactionManager(dataSource));
    tx.execute(() -> {
      order.confirm(Instant.parse("2026-10-22T00:01:00Z")); orders.save(order); outbox.appendAll(order.domainEvents()); return order;
    });
    assertThat(orders.findByIdAndCustomer(order.id(), order.customerReference()).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isOne();
  }

  @Test void persistsCompleteVersionedPayloadsInAggregateOrderAndMakesHistoryImmutable() throws Exception {
    Instant sameTime = Instant.parse("2026-10-22T00:00:00Z");
    Order order = sample(46, "46464646-4646-4646-4646-464646464646", sameTime);
    orders.save(order);
    outbox.appendAll(order.domainEvents());
    JsonNode created = new ObjectMapper().readTree(jdbc.queryForObject(
        "select payload::text from outbox_events where aggregate_version=0", String.class));
    assertThat(created.path("customer").path("reference").asText()).isEqualTo("customer-1");
    assertThat(created.path("items").get(0).path("productReference").asText()).isEqualTo("product-1");
    assertThat(created.path("items").get(0).path("unitPrice").path("amount").decimalValue()).isEqualByComparingTo("99.90");
    assertThat(created.path("shippingAddress").get("line2").isNull()).isTrue();
    assertThat(created.has("internalId")).isFalse();

    order.clearDomainEvents(); order.confirm(sameTime); orders.save(order); outbox.appendAll(order.domainEvents());
    order.clearDomainEvents(); order.cancel("Changed mind", sameTime); orders.save(order); outbox.appendAll(order.domainEvents());
    assertThat(jdbc.queryForList("select aggregate_version from outbox_events order by aggregate_version", Long.class))
        .containsExactly(0L, 1L, 2L);
    assertThat(jdbc.queryForList("select correlation_id from outbox_events", UUID.class)).doesNotContainNull();
    assertThatThrownBy(() -> jdbc.update("update outbox_events set payload='{}'::jsonb where aggregate_version=0"));
    assertThatThrownBy(() -> jdbc.update("delete from outbox_events where aggregate_version=0"));
    assertThatCode(() -> jdbc.update("update outbox_events set attempts=attempts+1 where aggregate_version=0"))
        .doesNotThrowAnyException();
  }

  @Test void databaseRejectsDuplicateAggregateVersionAndInvalidEnvelopeContract() {
    Order order = sample(47, "47474747-4747-4747-4747-474747474747", Instant.parse("2026-10-22T00:00:00Z"));
    orders.save(order); outbox.appendAll(order.domainEvents());
    assertThatThrownBy(() -> outbox.appendAll(order.domainEvents()));
    assertThatThrownBy(() -> jdbc.update("""
        insert into outbox_events(internal_id,event_id,aggregate_type,aggregate_public_id,aggregate_internal_id,
          aggregate_version,event_type,event_version,correlation_id,payload,occurred_at)
        values (9999,gen_random_uuid(),'Wrong',gen_random_uuid(),9999,-1,'Bad',1,gen_random_uuid(),'null'::jsonb,now())"""));
  }

  @Test void idempotencyClaimReplaysSameHashAndRejectsDifferentHash() {
    PostgresIdempotencyRepository idempotency = new PostgresIdempotencyRepository(jdbc);
    Order order = sample(50, "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", Instant.parse("2026-10-22T00:00:00Z")); orders.save(order);
    assertThat(idempotency.claim("customer-1", "key-1", "a".repeat(64), order.internalId(), order.id(), order.createdAt()))
        .isEqualTo(IdempotencyClaim.created(order.id()));
    assertThat(idempotency.claim("customer-1", "key-1", "a".repeat(64), order.internalId(), order.id(), order.createdAt()))
        .isEqualTo(IdempotencyClaim.replay(order.id()));
    assertThatThrownBy(() -> idempotency.claim("customer-1", "key-1", "b".repeat(64), order.internalId(), order.id(), order.createdAt()))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  @Test void replayAndConflictRemainUsableInsideTransaction() {
    PostgresIdempotencyRepository idempotency = new PostgresIdempotencyRepository(jdbc);
    Order original = sample(60, "60606060-6060-6060-6060-606060606060", Instant.parse("2026-10-22T00:00:00Z"));
    Order candidate = sample(70, "70707070-7070-7070-7070-707070707070", Instant.parse("2026-10-22T00:00:01Z"));
    orders.save(original); orders.save(candidate);
    TransactionRunner tx = new SpringTransactionRunner(new DataSourceTransactionManager(dataSource));
    tx.execute(() -> idempotency.claim("customer-1", "key-tx", "c".repeat(64), original.internalId(), original.id(), original.createdAt()));

    IdempotencyClaim replay = tx.execute(() -> {
      IdempotencyClaim result = idempotency.claim("customer-1", "key-tx", "c".repeat(64), candidate.internalId(), candidate.id(), candidate.createdAt());
      assertThat(jdbc.queryForObject("select 1", Integer.class)).isOne();
      return result;
    });
    assertThat(replay).isEqualTo(IdempotencyClaim.replay(original.id()));
    assertThatThrownBy(() -> tx.execute(() -> idempotency.claim(
        "customer-1", "key-tx", "d".repeat(64), candidate.internalId(), candidate.id(), candidate.createdAt())))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  @Test void concurrentClaimsReturnOneOriginalOrderIdentifier() throws Exception {
    Order first = sample(80, "80808080-8080-8080-8080-808080808080", Instant.parse("2026-10-22T00:00:00Z"));
    Order second = sample(90, "90909090-9090-9090-9090-909090909090", Instant.parse("2026-10-22T00:00:01Z"));
    orders.save(first); orders.save(second);
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<IdempotencyClaim> left = pool.submit(() -> concurrentClaim(first, barrier));
      Future<IdempotencyClaim> right = pool.submit(() -> concurrentClaim(second, barrier));
      List<IdempotencyClaim> claims = List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
      assertThat(claims).extracting(IdempotencyClaim::disposition)
          .containsExactlyInAnyOrder(IdempotencyClaim.Disposition.CREATED, IdempotencyClaim.Disposition.REPLAY);
      assertThat(claims).extracting(IdempotencyClaim::orderId).containsOnly(claims.get(0).orderId());
    } finally {
      pool.shutdownNow();
    }
  }

  private IdempotencyClaim concurrentClaim(Order candidate, CyclicBarrier barrier) throws Exception {
    JdbcTemplate threadJdbc = new JdbcTemplate(dataSource);
    PostgresIdempotencyRepository repository = new PostgresIdempotencyRepository(threadJdbc);
    TransactionRunner tx = new SpringTransactionRunner(new DataSourceTransactionManager(dataSource));
    barrier.await(5, TimeUnit.SECONDS);
    return tx.execute(() -> repository.claim("customer-1", "key-race", "e".repeat(64),
        candidate.internalId(), candidate.id(), candidate.createdAt()));
  }

  private static Order sample(long internalId, String publicId, Instant at) {
    return Order.create(new OrderInternalId(internalId), new OrderId(UUID.fromString(publicId)), new CustomerReference("customer-1"), "Ada",
        List.of(new OrderItem(new OrderItemInternalId(internalId + 1), new ProductReference("product-1"), null, "SKU-1", "Phone", Money.nzd(new BigDecimal("99.90")), 2)),
        new AddressSnapshot("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null), at);
  }

  private static PGSimpleDataSource schemaDataSource(String schema) {
    PGSimpleDataSource source = new PGSimpleDataSource();
    String url = POSTGRES.getJdbcUrl();
    source.setURL(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
    source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
    return source;
  }
}
