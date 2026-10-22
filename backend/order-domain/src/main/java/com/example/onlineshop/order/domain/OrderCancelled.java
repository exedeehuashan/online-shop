package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.Objects;

public record OrderCancelled(OrderId orderId, OrderInternalId orderInternalId, Instant occurredAt, String reason) implements DomainEvent {
  public OrderCancelled { Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId); Objects.requireNonNull(occurredAt); reason = Validation.required(reason, 500, "cancellation reason"); }
  @Override public String eventType() { return "OrderCancelled"; }
  @Override public int eventVersion() { return 1; }
}
