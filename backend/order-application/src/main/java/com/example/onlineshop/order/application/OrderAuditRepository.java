package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

public interface OrderAuditRepository {
  void append(Order order, UUID actorId, String action, Instant occurredAt, String correlationId);
}
