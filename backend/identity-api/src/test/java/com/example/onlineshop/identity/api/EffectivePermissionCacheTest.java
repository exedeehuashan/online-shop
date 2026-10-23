package com.example.onlineshop.identity.api;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EffectivePermissionCacheTest {
  @Test void cachesWithinBoundAndReloadsAfterExpiryOrInvalidation() {
    MutableClock clock = new MutableClock();
    EffectivePermissionCache cache = new EffectivePermissionCache(Duration.ofSeconds(30), clock);
    UUID user = UUID.randomUUID();
    AtomicInteger loads = new AtomicInteger();
    assertThat(cache.get(user, () -> Set.of("permission:" + loads.incrementAndGet()))).containsExactly("permission:1");
    assertThat(cache.get(user, () -> Set.of("permission:" + loads.incrementAndGet()))).containsExactly("permission:1");
    clock.now = clock.now.plusSeconds(31);
    assertThat(cache.get(user, () -> Set.of("permission:" + loads.incrementAndGet()))).containsExactly("permission:2");
    cache.invalidate(user);
    assertThat(cache.get(user, () -> Set.of("permission:" + loads.incrementAndGet()))).containsExactly("permission:3");
  }

  @Test void rejectsUnboundedOrDisabledCaching() {
    assertThatThrownBy(() -> new EffectivePermissionCache(Duration.ZERO, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EffectivePermissionCache(Duration.ofMinutes(6), Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void oidcConfigurationFailsClosedForMissingSettingsAndUnsafeAlgorithms() {
    OidcSecurityConfiguration configuration = new OidcSecurityConfiguration();
    assertThatThrownBy(() -> configuration.jwtDecoder("", "https://issuer.example", "shop-api", "RS256"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> configuration.jwtDecoder("https://issuer.example/jwks", "https://issuer.example", "shop-api", "HS256"))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-24T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
}
