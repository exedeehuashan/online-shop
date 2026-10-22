package com.example.onlineshop.order.domain;

public record OrderItemInternalId(long value) {
  public OrderItemInternalId { if (value <= 0) throw Validation.invalid("order item internal ID must be a positive 63-bit value"); }
}
