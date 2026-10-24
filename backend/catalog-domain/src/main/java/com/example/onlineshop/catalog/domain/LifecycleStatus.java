package com.example.onlineshop.catalog.domain;

public enum LifecycleStatus {
  DRAFT, ACTIVE, ARCHIVED;

  public void requireMutable(String aggregate) {
    if (this == ARCHIVED) throw new ProductValidationException(aggregate + " is archived");
  }
}
