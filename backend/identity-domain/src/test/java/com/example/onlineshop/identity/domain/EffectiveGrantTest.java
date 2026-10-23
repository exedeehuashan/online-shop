package com.example.onlineshop.identity.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EffectiveGrantTest {
  private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
  @Test void onlyActiveUnexpiredGrantIsEffective() {
    var code = new PermissionCode("order:read:self");
    assertThat(new EffectiveGrant("ACTIVE", "ACTIVE", "ACTIVE", NOW.plusSeconds(1), code).isEffectiveAt(NOW)).isTrue();
    assertThat(new EffectiveGrant("DISABLED", "ACTIVE", "ACTIVE", null, code).isEffectiveAt(NOW)).isFalse();
    assertThat(new EffectiveGrant("ACTIVE", "ARCHIVED", "ACTIVE", null, code).isEffectiveAt(NOW)).isFalse();
    assertThat(new EffectiveGrant("ACTIVE", "ACTIVE", "ARCHIVED", null, code).isEffectiveAt(NOW)).isFalse();
    assertThat(new EffectiveGrant("ACTIVE", "ACTIVE", "ACTIVE", NOW, code).isEffectiveAt(NOW)).isFalse();
  }
  @Test void permissionCodesMustBeNormalizedConstants() {
    assertThatThrownBy(() -> new PermissionCode(" Catalog:Write ")).isInstanceOf(IllegalArgumentException.class);
  }
}
