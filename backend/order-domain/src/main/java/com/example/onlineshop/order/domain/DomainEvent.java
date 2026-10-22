package com.example.onlineshop.order.domain;

import java.time.Instant;

public sealed interface DomainEvent permits OrderCreated, OrderConfirmed, OrderCancelled {
  OrderId orderId();
  OrderInternalId orderInternalId();
  Instant occurredAt();
  String eventType();
  int eventVersion();
}
