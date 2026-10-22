package com.example.onlineshop.order.application;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(String customerReference, String customerName, List<Item> items, Address shippingAddress) {
  public CreateOrderCommand { items = items == null ? null : List.copyOf(items); }
  public record Item(String productReference, String sku, String productName, BigDecimal unitPrice, int quantity) {}
  public record Address(String recipient, String line1, String line2, String city, String region,
                        String postalCode, String countryCode, String phone) {}
}
