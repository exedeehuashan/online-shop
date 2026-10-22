package com.example.onlineshop.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, String name, String description, BigDecimal price, String currency,
                      int stockQuantity, Instant createdAt, Instant updatedAt) {
  public Product {
    Objects.requireNonNull(id, "id"); Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
    name = text(name, "name", 1, 160); description = text(description, "description", 0, 2000);
    if (price == null || price.scale() > 2 || price.signum() < 0 || price.precision() > 19) throw new ProductValidationException("price must be a non-negative decimal with at most 2 fraction digits");
    try { currency = Currency.getInstance(currency).getCurrencyCode(); } catch (RuntimeException e) { throw new ProductValidationException("currency must be an ISO 4217 code"); }
    if (!currency.matches("[A-Z]{3}")) throw new ProductValidationException("currency must be uppercase ISO 4217");
    if (stockQuantity < 0) throw new ProductValidationException("stockQuantity must be non-negative");
  }
  private static String text(String value,String field,int min,int max) {
    if (value == null || value.length() < min || value.length() > max || (min > 0 && value.isBlank())) throw new ProductValidationException(field+" length must be between "+min+" and "+max);
    return value;
  }
}
