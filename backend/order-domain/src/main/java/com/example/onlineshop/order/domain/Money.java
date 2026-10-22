package com.example.onlineshop.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, String currency) {
  public Money {
    if (amount == null) throw Validation.invalid("amount is required");
    currency = Validation.required(currency, 3, "currency").toUpperCase();
    if (!"NZD".equals(currency)) throw Validation.invalid("currency must be NZD");
    if (amount.signum() < 0) throw Validation.invalid("amount must be non-negative");
    try { amount = amount.setScale(2, RoundingMode.UNNECESSARY); }
    catch (ArithmeticException exception) { throw Validation.invalid("amount must have at most two decimal places"); }
    if (amount.precision() > 19) throw Validation.invalid("amount exceeds numeric(19,2)");
  }

  public static Money nzd(BigDecimal amount) { return new Money(amount, "NZD"); }
  public static Money zeroNzd() { return nzd(BigDecimal.ZERO); }

  public Money add(Money other) {
    if (other == null) throw Validation.invalid("money to add is required");
    if (!currency.equals(other.currency)) throw Validation.invalid("currencies must match");
    return new Money(amount.add(other.amount), currency);
  }

  public Money multiply(int quantity) {
    if (quantity <= 0) throw Validation.invalid("quantity must be positive");
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
  }
}
