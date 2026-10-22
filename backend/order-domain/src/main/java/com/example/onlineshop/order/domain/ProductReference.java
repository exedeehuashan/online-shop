package com.example.onlineshop.order.domain;

public record ProductReference(String value) {
  public ProductReference { value = Validation.requiredOpaque(value, 128, "product reference"); }
}
