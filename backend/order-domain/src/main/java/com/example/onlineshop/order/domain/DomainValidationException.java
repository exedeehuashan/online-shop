package com.example.onlineshop.order.domain;

public final class DomainValidationException extends RuntimeException {
  private final DomainErrorCode code;

  public DomainValidationException(DomainErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public DomainErrorCode code() { return code; }
}
