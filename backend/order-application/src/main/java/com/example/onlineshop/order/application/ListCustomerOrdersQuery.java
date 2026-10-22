package com.example.onlineshop.order.application;

public record ListCustomerOrdersQuery(String customerReference, int page, int size) {
  public ListCustomerOrdersQuery {
    if (page < 0) throw new IllegalArgumentException("page must be non-negative");
    if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
  }
}
