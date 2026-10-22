package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderCreated(UUID eventId, OrderId orderId, OrderInternalId orderInternalId, long aggregateVersion,
                           Instant occurredAt, UUID correlationId, CustomerReference customerReference,
                           String customerName, List<ItemSnapshot> items, Money total,
                           AddressSnapshot shippingAddress, OrderStatus status) implements DomainEvent {
  public OrderCreated {
    Objects.requireNonNull(eventId); Objects.requireNonNull(orderId); Objects.requireNonNull(orderInternalId);
    if (aggregateVersion != 0) throw Validation.invalid("created aggregate version must be zero");
    Objects.requireNonNull(occurredAt); Objects.requireNonNull(correlationId); Objects.requireNonNull(customerReference);
    customerName = Validation.required(customerName, 200, "customer name snapshot");
    items = List.copyOf(Objects.requireNonNull(items));
    Objects.requireNonNull(total); Objects.requireNonNull(shippingAddress); Objects.requireNonNull(status);
  }
  public record ItemSnapshot(ProductReference productReference, String sku, String productName,
                             Money unitPrice, int quantity, Money lineTotal) {
    public ItemSnapshot {
      Objects.requireNonNull(productReference); sku = Validation.required(sku, 100, "SKU");
      productName = Validation.required(productName, 300, "product name snapshot");
      Objects.requireNonNull(unitPrice); Objects.requireNonNull(lineTotal);
      if (quantity <= 0) throw Validation.invalid("quantity must be positive");
    }
    static ItemSnapshot from(OrderItem item) {
      return new ItemSnapshot(item.productReference(), item.sku(), item.productNameSnapshot(),
          item.unitPriceSnapshot(), item.quantity(), item.lineTotal());
    }
  }
  @Override public String eventType() { return "OrderCreated"; }
  @Override public int eventVersion() { return 1; }
}
