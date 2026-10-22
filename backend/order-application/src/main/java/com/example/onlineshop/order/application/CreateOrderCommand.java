package com.example.onlineshop.order.application;

import java.math.BigDecimal;
import java.util.List;
import com.example.onlineshop.order.domain.DomainErrorCode;
import com.example.onlineshop.order.domain.DomainValidationException;

public record CreateOrderCommand(String customerReference, String customerName, List<Item> items, Address shippingAddress) {
  public CreateOrderCommand {
    if (items != null && items.stream().anyMatch(java.util.Objects::isNull)) {
      throw new DomainValidationException(DomainErrorCode.INVALID_VALUE, "order item is required");
    }
    items = items == null ? null : List.copyOf(items);
  }
  public record Item(String productReference, String sku, String productName, BigDecimal unitPrice, int quantity) {}
  public record Address(String recipient, String line1, String line2, String city, String region,
                        String postalCode, String countryCode, String phone) {}
}
