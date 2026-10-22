package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent permits OrderCreated, OrderConfirmed, OrderCancelled {
  UUID eventId();
  OrderId orderId();
  OrderInternalId orderInternalId();
  long aggregateVersion();
  Instant occurredAt();
  UUID correlationId();
  String eventType();
  int eventVersion();
}
