package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.util.Objects;

public final class GetOrderHandler implements GetOrderUseCase {
  private final OrderRepository orders;
  public GetOrderHandler(OrderRepository orders) { this.orders = Objects.requireNonNull(orders); }
  @Override public Order handle(GetOrderQuery query) {
    Objects.requireNonNull(query, "query");
    return orders.findByIdAndCustomer(Objects.requireNonNull(query.orderId()), new CustomerReference(query.customerReference()))
        .orElseThrow(OrderNotFoundException::new);
  }
}
