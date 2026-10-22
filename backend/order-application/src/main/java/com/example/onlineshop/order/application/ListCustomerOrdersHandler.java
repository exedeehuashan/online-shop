package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.util.*;

public final class ListCustomerOrdersHandler implements ListCustomerOrdersUseCase {
  private final OrderRepository orders;
  public ListCustomerOrdersHandler(OrderRepository orders) { this.orders = Objects.requireNonNull(orders); }
  @Override public List<Order> handle(ListCustomerOrdersQuery query) {
    Objects.requireNonNull(query, "query");
    return List.copyOf(orders.findByCustomer(new CustomerReference(query.customerReference()), query.page(), query.size()));
  }
}
