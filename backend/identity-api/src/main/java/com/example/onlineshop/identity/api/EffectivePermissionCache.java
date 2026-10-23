package com.example.onlineshop.identity.api;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class EffectivePermissionCache {
  private final Duration ttl;
  private final Clock clock;
  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

  EffectivePermissionCache(Duration ttl, Clock clock) {
    if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(5)) > 0)
      throw new IllegalArgumentException("permission cache ttl must be > 0 and <= 5 minutes");
    this.ttl = ttl; this.clock = clock;
  }
  Set<String> get(UUID user, Supplier<Set<String>> loader) {
    Instant now = clock.instant();
    Entry value = entries.compute(user, (ignored, old) -> old != null && old.expiresAt.isAfter(now)
        ? old : new Entry(Set.copyOf(loader.get()), now.plus(ttl)));
    return value.permissions;
  }
  void invalidate(UUID user) { entries.remove(user); }
  void invalidateAll() { entries.clear(); }
  private record Entry(Set<String> permissions, Instant expiresAt) {}
}
