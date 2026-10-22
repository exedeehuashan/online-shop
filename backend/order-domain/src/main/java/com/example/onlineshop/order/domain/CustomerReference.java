package com.example.onlineshop.order.domain;

public record CustomerReference(String value) {
  public CustomerReference { value = Validation.required(value, 128, "customer reference"); }
}
