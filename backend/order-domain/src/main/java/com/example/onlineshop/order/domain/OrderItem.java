package com.example.onlineshop.order.domain;

public record OrderItem(OrderItemInternalId internalId, ProductReference productReference, Long productInternalId,
                        String sku, String productNameSnapshot, Money unitPriceSnapshot, int quantity, Money lineTotal) {
  public OrderItem(OrderItemInternalId internalId, ProductReference productReference, Long productInternalId,
                   String sku, String productNameSnapshot, Money unitPriceSnapshot, int quantity) {
    this(internalId, productReference, productInternalId, sku, productNameSnapshot, unitPriceSnapshot, quantity,
        requiredUnitPrice(unitPriceSnapshot).multiply(quantity));
  }

  public OrderItem {
    if (internalId == null) throw Validation.invalid("order item internal ID is required");
    if (productReference == null) throw Validation.invalid("product reference is required");
    if (productInternalId != null && productInternalId <= 0) throw Validation.invalid("product internal ID must be positive");
    sku = Validation.required(sku, 100, "SKU");
    productNameSnapshot = Validation.required(productNameSnapshot, 300, "product name snapshot");
    requiredUnitPrice(unitPriceSnapshot);
    if (quantity <= 0) throw Validation.invalid("quantity must be positive");
    Money expected = unitPriceSnapshot.multiply(quantity);
    if (!expected.equals(lineTotal)) throw Validation.invalid("line total must equal price multiplied by quantity");
  }

  private static Money requiredUnitPrice(Money unitPrice) {
    if (unitPrice == null) throw Validation.invalid("unit price snapshot is required");
    return unitPrice;
  }
}
