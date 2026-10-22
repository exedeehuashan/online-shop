package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;
import java.util.Objects;

public record IdempotencyClaim(Disposition disposition, OrderId orderId) {
  public IdempotencyClaim {
    Objects.requireNonNull(disposition, "disposition");
    Objects.requireNonNull(orderId, "orderId");
  }

  public static IdempotencyClaim created(OrderId orderId) {
    return new IdempotencyClaim(Disposition.CREATED, orderId);
  }

  public static IdempotencyClaim replay(OrderId orderId) {
    return new IdempotencyClaim(Disposition.REPLAY, orderId);
  }

  public enum Disposition { CREATED, REPLAY }
}
