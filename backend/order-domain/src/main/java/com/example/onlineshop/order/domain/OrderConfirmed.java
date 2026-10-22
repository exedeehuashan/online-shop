package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.Objects;

public record OrderConfirmed(OrderId orderId, OrderInternalId orderInternalId, Instant occurredAt) implements DomainEvent {
  public OrderConfirmed { Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId); Objects.requireNonNull(occurredAt); }
  @Override public String eventType() { return "OrderConfirmed"; }
  @Override public int eventVersion() { return 1; }
}
