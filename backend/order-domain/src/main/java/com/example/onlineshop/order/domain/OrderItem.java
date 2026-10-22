package com.example.onlineshop.order.domain;

import java.util.Objects;

public record OrderItem(OrderItemInternalId internalId, ProductReference productReference, Long productInternalId,
                        String sku, String productNameSnapshot, Money unitPriceSnapshot, int quantity, Money lineTotal) {
  public OrderItem(OrderItemInternalId internalId, ProductReference productReference, Long productInternalId,
                   String sku, String productNameSnapshot, Money unitPriceSnapshot, int quantity) {
    this(internalId, productReference, productInternalId, sku, productNameSnapshot, unitPriceSnapshot, quantity,
        Objects.requireNonNull(unitPriceSnapshot, "unitPriceSnapshot").multiply(quantity));
  }

  public OrderItem {
    Objects.requireNonNull(internalId, "internalId");
    Objects.requireNonNull(productReference, "productReference");
    if (productInternalId != null && productInternalId <= 0) throw Validation.invalid("product internal ID must be positive");
    sku = Validation.required(sku, 100, "SKU");
    productNameSnapshot = Validation.required(productNameSnapshot, 300, "product name snapshot");
    Objects.requireNonNull(unitPriceSnapshot, "unitPriceSnapshot");
    if (quantity <= 0) throw Validation.invalid("quantity must be positive");
    Money expected = unitPriceSnapshot.multiply(quantity);
    if (!expected.equals(lineTotal)) throw Validation.invalid("line total must equal price multiplied by quantity");
  }
}
