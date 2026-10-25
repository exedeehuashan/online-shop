package com.example.onlineshop.order.api;

import java.util.Set;
import java.util.UUID;

public record OrderPrincipal(UUID userId, Set<String> permissions) {
  public OrderPrincipal { permissions = Set.copyOf(permissions); }
  boolean permits(String permission) { return permissions.contains(permission); }
}
