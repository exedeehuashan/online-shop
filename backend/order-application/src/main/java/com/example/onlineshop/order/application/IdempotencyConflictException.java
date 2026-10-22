package com.example.onlineshop.order.application;

public final class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException() { super("idempotency key is already associated with a different request"); }
}
