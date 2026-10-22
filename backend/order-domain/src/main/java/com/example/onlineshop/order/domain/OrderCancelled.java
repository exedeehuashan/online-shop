package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCancelled(UUID eventId, OrderId orderId, OrderInternalId orderInternalId, long aggregateVersion,
                             Instant occurredAt, UUID correlationId, OrderStatus status, String reason) implements DomainEvent {
  public OrderCancelled { Objects.requireNonNull(eventId); Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId); if (aggregateVersion < 1) throw Validation.invalid("cancelled aggregate version must be positive"); Objects.requireNonNull(occurredAt); Objects.requireNonNull(correlationId); if (status != OrderStatus.CANCELLED) throw Validation.invalid("cancelled event status must be CANCELLED"); reason = Validation.required(reason, 500, "cancellation reason"); }
  @Override public String eventType() { return "OrderCancelled"; }
  @Override public int eventVersion() { return 1; }
}
