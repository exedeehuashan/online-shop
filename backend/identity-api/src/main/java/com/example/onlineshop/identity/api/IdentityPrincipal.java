package com.example.onlineshop.identity.api;

import java.util.Set;
import java.util.UUID;

public record IdentityPrincipal(UUID id, String displayName, Set<String> permissions) {
  public IdentityPrincipal { permissions = Set.copyOf(permissions); }
  public boolean permits(String code) { return permissions.contains(code); }
}
