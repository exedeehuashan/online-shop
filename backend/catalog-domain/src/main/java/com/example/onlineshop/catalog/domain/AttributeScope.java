package com.example.onlineshop.catalog.domain;

public enum AttributeScope {
  PRODUCT, SKU, BOTH;
  public boolean permitsProduct() { return this == PRODUCT || this == BOTH; }
  public boolean permitsSku() { return this == SKU || this == BOTH; }
}
