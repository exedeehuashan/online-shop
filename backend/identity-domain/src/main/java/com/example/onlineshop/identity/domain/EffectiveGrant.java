package com.example.onlineshop.identity.domain;

import java.time.Instant;

public record EffectiveGrant(String userStatus, String roleStatus, String permissionStatus,
                             Instant expiresAt, PermissionCode permission) {
  public boolean isEffectiveAt(Instant now) {
    return "ACTIVE".equals(userStatus) && "ACTIVE".equals(roleStatus)
        && "ACTIVE".equals(permissionStatus) && (expiresAt == null || expiresAt.isAfter(now));
  }
}
