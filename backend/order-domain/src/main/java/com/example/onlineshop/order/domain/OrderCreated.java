package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.Objects;

public record OrderCreated(OrderId orderId, OrderInternalId orderInternalId, Instant occurredAt) implements DomainEvent {
  public OrderCreated { Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId); Objects.requireNonNull(occurredAt); }
  @Override public String eventType() { return "OrderCreated"; }
  @Override public int eventVersion() { return 1; }
}
