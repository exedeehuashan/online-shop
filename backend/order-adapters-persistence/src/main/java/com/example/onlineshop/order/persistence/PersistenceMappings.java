package com.example.onlineshop.order.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.SqlTypes;

/** Hibernate mappings used only to validate the Flyway-owned schema; JDBC remains the runtime adapter. */
public final class PersistenceMappings {
  private PersistenceMappings() {}

  public static void validate(DataSource dataSource) {
    StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .applySetting(AvailableSettings.DATASOURCE, dataSource)
        .applySetting(AvailableSettings.JAKARTA_HBM2DDL_DATABASE_ACTION, "validate")
        .applySetting(AvailableSettings.SHOW_SQL, false)
        .build();
    try (SessionFactory ignored = new MetadataSources(registry)
        .addAnnotatedClass(OrderMapping.class)
        .addAnnotatedClass(OrderItemMapping.class)
        .addAnnotatedClass(IdempotencyMapping.class)
        .addAnnotatedClass(OutboxMapping.class)
        .buildMetadata().buildSessionFactory()) {
      // Building the SessionFactory executes Hibernate schema validation.
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  @Entity(name = "OrderSchemaMapping")
  @Table(name = "orders")
  static class OrderMapping {
    @Id @Column(name = "internal_id") long internalId;
    @Column(name = "public_id", nullable = false, unique = true) UUID publicId;
    @Column(name = "customer_ref", nullable = false, length = 128) String customerRef;
    @Column(name = "customer_internal_id") Long customerInternalId;
    @Column(name = "customer_name_snapshot", nullable = false, length = 200) String customerName;
    @Column(nullable = false, length = 32) String status;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, columnDefinition = "char(3)") String currency;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2) BigDecimal total;
    @Column(name = "item_count", nullable = false) int itemCount;
    @Column(nullable = false, length = 200) String recipient;
    @Column(name = "address_line1", nullable = false, length = 200) String addressLine1;
    @Column(name = "address_line2", length = 200) String addressLine2;
    @Column(nullable = false, length = 100) String city;
    @Column(length = 100) String region;
    @Column(name = "postal_code", nullable = false, length = 32) String postalCode;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "country_code", nullable = false, columnDefinition = "char(2)") String countryCode;
    @Column(length = 32) String phone;
    @Column(name = "cancellation_reason", length = 500) String cancellationReason;
    @Version @Column(nullable = false) long version;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
  }

  @Entity(name = "OrderItemSchemaMapping")
  @Table(name = "order_items")
  static class OrderItemMapping {
    @Id @Column(name = "internal_id") long internalId;
    @Column(name = "order_internal_id", nullable = false) long orderInternalId;
    @Column(name = "product_ref", nullable = false, length = 128) String productRef;
    @Column(name = "product_internal_id") Long productInternalId;
    @Column(nullable = false, length = 100) String sku;
    @Column(name = "product_name_snapshot", nullable = false, length = 300) String productName;
    @Column(name = "unit_amount_snapshot", nullable = false, precision = 19, scale = 2) BigDecimal unitAmount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, columnDefinition = "char(3)") String currency;
    @Column(nullable = false) int quantity;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2) BigDecimal lineTotal;
  }

  @Embeddable
  static class IdempotencyKey implements Serializable {
    @Column(name = "customer_ref", length = 128) String customerRef;
    @Column(name = "idempotency_key", length = 128) String idempotencyKey;
  }

  @Entity(name = "IdempotencySchemaMapping")
  @Table(name = "order_idempotency")
  static class IdempotencyMapping {
    @EmbeddedId IdempotencyKey id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "request_hash", nullable = false, columnDefinition = "char(64)") String requestHash;
    @Column(name = "order_internal_id", nullable = false) long orderInternalId;
    @Column(name = "order_public_id", nullable = false) UUID orderPublicId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
  }

  @Entity(name = "OutboxSchemaMapping")
  @Table(name = "outbox_events")
  static class OutboxMapping {
    @Id @Column(name = "internal_id") long internalId;
    @Column(name = "event_id", nullable = false, unique = true) UUID eventId;
    @Column(name = "aggregate_type", nullable = false, length = 100) String aggregateType;
    @Column(name = "aggregate_public_id", nullable = false) UUID aggregatePublicId;
    @Column(name = "aggregate_internal_id", nullable = false) long aggregateInternalId;
    @Column(name = "event_type", nullable = false, length = 150) String eventType;
    @Column(name = "event_version", nullable = false) int eventVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") Map<String, Object> payload;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    @Column(name = "published_at") Instant publishedAt;
    @Column(nullable = false) int attempts;
  }
}
