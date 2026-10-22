package com.example.onlineshop.order.domain;

public record OrderInternalId(long value) {
  public OrderInternalId { if (value <= 0) throw Validation.invalid("order internal ID must be a positive 63-bit value"); }
}
