package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderConfirmed(UUID eventId, OrderId orderId, OrderInternalId orderInternalId, long aggregateVersion,
                             Instant occurredAt, UUID correlationId, OrderStatus status) implements DomainEvent {
  public OrderConfirmed { Objects.requireNonNull(eventId); Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId); if (aggregateVersion < 1) throw Validation.invalid("confirmed aggregate version must be positive"); Objects.requireNonNull(occurredAt); Objects.requireNonNull(correlationId); if (status != OrderStatus.CONFIRMED) throw Validation.invalid("confirmed event status must be CONFIRMED"); }
  @Override public String eventType() { return "OrderConfirmed"; }
  @Override public int eventVersion() { return 1; }
}
