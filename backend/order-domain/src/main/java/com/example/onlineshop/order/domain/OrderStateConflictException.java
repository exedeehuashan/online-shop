package com.example.onlineshop.order.domain;

public final class OrderStateConflictException extends RuntimeException {
  private final DomainErrorCode code = DomainErrorCode.ORDER_STATE_CONFLICT;

  public OrderStateConflictException(String message) { super(message); }
  public DomainErrorCode code() { return code; }
}
