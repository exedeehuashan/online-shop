package com.example.onlineshop.order.application;

public final class OptimisticOrderLockException extends RuntimeException {
  public OptimisticOrderLockException() { super("order was concurrently modified"); }
}
