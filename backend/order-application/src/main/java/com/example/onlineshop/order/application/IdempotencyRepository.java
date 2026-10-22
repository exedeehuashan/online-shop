package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;
import com.example.onlineshop.order.domain.OrderInternalId;
import java.time.Instant;

public interface IdempotencyRepository {
  IdempotencyClaim claim(String customer, String key, String hash, OrderInternalId internalId, OrderId publicId, Instant at);
}
