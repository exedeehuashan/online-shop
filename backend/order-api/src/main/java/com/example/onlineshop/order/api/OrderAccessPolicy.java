package com.example.onlineshop.order.api;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class OrderAccessPolicy {
  OrderPrincipal principal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof OrderPrincipal principal) return principal;
    return null;
  }
  OrderPrincipal require(String permission) {
    OrderPrincipal principal = principal();
    if (principal == null || !principal.permits(permission)) throw new AccessDeniedException("Request denied");
    return principal;
  }
  OrderPrincipal requireBoth(String first, String second) {
    OrderPrincipal principal = require(first);
    if (!principal.permits(second)) throw new AccessDeniedException("Request denied");
    return principal;
  }
  OrderPrincipal authorizeV1(String customerId, boolean mutation) {
    OrderPrincipal principal = principal();
    if (principal == null) return null; // legacy mode
    boolean self = principal.userId().toString().equals(customerId);
    if (self && principal.permits("order:read:self")) return principal;
    if (!principal.permits("order:read:any") || (mutation && !principal.permits("order:manage")))
      throw new AccessDeniedException("Request denied");
    return principal;
  }
}
