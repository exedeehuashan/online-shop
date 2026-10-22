package com.example.onlineshop.order.application;

public final class OrderNotFoundException extends RuntimeException {
  public OrderNotFoundException() { super("order was not found"); }
}
