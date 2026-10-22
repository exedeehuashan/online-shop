package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresOutboxRepository implements DomainEventRepository {
  private final JdbcTemplate jdbc;
  private final InternalIdGenerator internalIds;
  private final ObjectMapper json;
  public PostgresOutboxRepository(JdbcTemplate jdbc, InternalIdGenerator internalIds, PublicIdGenerator eventIds) {
    this(jdbc, internalIds, configuredMapper());
  }
  public PostgresOutboxRepository(JdbcTemplate jdbc, InternalIdGenerator internalIds, ObjectMapper json) {
    this.jdbc = Objects.requireNonNull(jdbc); this.internalIds = Objects.requireNonNull(internalIds); this.json = Objects.requireNonNull(json);
  }
  @Override public void appendAll(List<DomainEvent> events) {
    for (DomainEvent event : events) {
      long internalId = internalIds.nextId();
      int inserted = jdbc.update("insert into outbox_events(internal_id,event_id,aggregate_type,aggregate_public_id,aggregate_internal_id,aggregate_version,event_type,event_version,correlation_id,payload,occurred_at) values (?,?,?,?,?,?,?,?,?,?::jsonb,?)",
          internalId, event.eventId(), "Order", event.orderId().value(), event.orderInternalId().value(), event.aggregateVersion(),
          event.eventType(), event.eventVersion(), event.correlationId(), payload(event), OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
      if (inserted != 1) throw new IllegalStateException("outbox insert affected " + inserted + " rows");
    }
  }
  private String payload(DomainEvent event) {
    Object payload;
    if (event instanceof OrderCreated created) {
      payload = new CreatedPayload(created.orderId().value(),
          new CustomerPayload(created.customerReference().value(), created.customerName()),
          created.status().name(), created.items().stream().map(ItemPayload::from).toList(), MoneyPayload.from(created.total()),
          AddressPayload.from(created.shippingAddress()), created.occurredAt());
    } else if (event instanceof OrderConfirmed confirmed) {
      payload = new ConfirmedPayload(confirmed.orderId().value(), confirmed.status().name(), confirmed.aggregateVersion(), confirmed.occurredAt());
    } else if (event instanceof OrderCancelled cancelled) {
      payload = new CancelledPayload(cancelled.orderId().value(), cancelled.status().name(), cancelled.aggregateVersion(), cancelled.reason(), cancelled.occurredAt());
    } else throw new IllegalArgumentException("unsupported domain event: " + event.getClass().getName());
    try { return json.writeValueAsString(payload); }
    catch (JsonProcessingException impossible) { throw new IllegalStateException("cannot serialize outbox payload", impossible); }
  }

  private static ObjectMapper configuredMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
  private record CustomerPayload(String reference, String name) {}
  private record MoneyPayload(BigDecimal amount, String currency) { static MoneyPayload from(Money money) { return new MoneyPayload(money.amount(), money.currency()); } }
  private record ItemPayload(String productReference, String sku, String productName, MoneyPayload unitPrice,
                             int quantity, MoneyPayload lineTotal) {
    static ItemPayload from(OrderCreated.ItemSnapshot item) { return new ItemPayload(item.productReference().value(), item.sku(), item.productName(), MoneyPayload.from(item.unitPrice()), item.quantity(), MoneyPayload.from(item.lineTotal())); }
  }
  private record AddressPayload(String recipient, String line1, String line2, String city, String region,
                                String postalCode, String countryCode, String phone) {
    static AddressPayload from(AddressSnapshot a) { return new AddressPayload(a.recipient(), a.line1(), a.line2(), a.city(), a.region(), a.postalCode(), a.countryCode(), a.phone()); }
  }
  private record CreatedPayload(UUID orderId, CustomerPayload customer, String status, List<ItemPayload> items,
                                MoneyPayload total, AddressPayload shippingAddress, Instant createdAt) {}
  private record ConfirmedPayload(UUID orderId, String status, long aggregateVersion, Instant confirmedAt) {}
  private record CancelledPayload(UUID orderId, String status, long aggregateVersion, String reason, Instant cancelledAt) {}
}
