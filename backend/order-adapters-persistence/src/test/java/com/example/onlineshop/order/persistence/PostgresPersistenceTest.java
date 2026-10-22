package com.example.onlineshop.order.persistence;

import static org.assertj.core.api.Assertions.*;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPersistenceTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
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

  @Test void idempotencyClaimReplaysSameHashAndRejectsDifferentHash() {
    PostgresIdempotencyRepository idempotency = new PostgresIdempotencyRepository(jdbc);
    Order order = sample(50, "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", Instant.parse("2026-10-22T00:00:00Z")); orders.save(order);
    assertThat(idempotency.claim("customer-1", "key-1", "a".repeat(64), order.internalId(), order.createdAt())).isEqualTo(IdempotencyClaim.CREATED);
    assertThat(idempotency.claim("customer-1", "key-1", "a".repeat(64), order.internalId(), order.createdAt())).isEqualTo(IdempotencyClaim.REPLAY);
    assertThatThrownBy(() -> idempotency.claim("customer-1", "key-1", "b".repeat(64), order.internalId(), order.createdAt()))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  private static Order sample(long internalId, String publicId, Instant at) {
    return Order.create(new OrderInternalId(internalId), new OrderId(UUID.fromString(publicId)), new CustomerReference("customer-1"), "Ada",
        List.of(new OrderItem(new OrderItemInternalId(internalId + 1), new ProductReference("product-1"), null, "SKU-1", "Phone", Money.nzd(new BigDecimal("99.90")), 2)),
        new AddressSnapshot("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null), at);
  }
}
