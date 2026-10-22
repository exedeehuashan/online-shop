package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;

public record ConfirmOrderCommand(String customerReference, OrderId orderId, Long expectedVersion) {
  public ConfirmOrderCommand(String customerReference, OrderId orderId) { this(customerReference, orderId, null); }
}
