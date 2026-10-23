package com.example.onlineshop.identity.api;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class EffectivePermissionCache {
  private final Duration ttl;
  private final Clock clock;
  private final int maximumEntries;
  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

  EffectivePermissionCache(Duration ttl, Clock clock) {
    this(ttl, clock, 10_000);
  }
  EffectivePermissionCache(Duration ttl, Clock clock, int maximumEntries) {
    if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(5)) > 0)
      throw new IllegalArgumentException("permission cache ttl must be > 0 and <= 5 minutes");
    if (maximumEntries < 1 || maximumEntries > 100_000) throw new IllegalArgumentException("permission cache maximum entries must be between 1 and 100000");
    this.ttl = ttl; this.clock = clock; this.maximumEntries = maximumEntries;
  }
  synchronized Set<String> get(UUID user, Supplier<Set<String>> loader) {
    Instant now = clock.instant();
    if (!entries.containsKey(user) && entries.size() >= maximumEntries) {
      entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
      if (entries.size() >= maximumEntries) entries.entrySet().stream()
          .min(Map.Entry.comparingByValue(Comparator.comparing(Entry::expiresAt)))
          .ifPresent(entry -> entries.remove(entry.getKey(), entry.getValue()));
    }
    Entry value = entries.compute(user, (ignored, old) -> old != null && old.expiresAt.isAfter(now)
        ? old : new Entry(Set.copyOf(loader.get()), now.plus(ttl)));
    return value.permissions;
  }
  void invalidate(UUID user) { entries.remove(user); }
  void invalidateAll() { entries.clear(); }
  int size() { return entries.size(); }
  private record Entry(Set<String> permissions, Instant expiresAt) {}
}
