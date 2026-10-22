package com.example.onlineshop.order.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class TimeOrderedIdGenerator implements InternalIdGenerator {
  private static final long EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
  private static final int NODE_BITS = 10;
  private static final int SEQUENCE_BITS = 12;
  private static final long MAX_NODE = (1L << NODE_BITS) - 1;
  private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
  private final long nodeId;
  private final Clock clock;
  private long lastMillis = -1;
  private long sequence;

  public TimeOrderedIdGenerator(long nodeId, Clock clock) {
    if (nodeId < 0 || nodeId > MAX_NODE) throw new IllegalArgumentException("node ID must be between 0 and " + MAX_NODE);
    this.nodeId = nodeId;
    this.clock = Objects.requireNonNull(clock);
  }

  @Override public synchronized long nextId() {
    long now = clock.millis();
    if (now < lastMillis) throw new IllegalStateException("clock moved backwards; refusing to generate an ID");
    if (now == lastMillis) {
      if (sequence == MAX_SEQUENCE) throw new IllegalStateException("ID sequence exhausted for millisecond " + now);
      sequence++;
    } else {
      // Zero is not a valid domain ID. Reserve it at the one instant where every
      // encoded component is zero, without shifting or overflowing the 63-bit range.
      sequence = now == EPOCH_MILLIS && nodeId == 0 ? 1 : 0;
      lastMillis = now;
    }
    long elapsed = now - EPOCH_MILLIS;
    if (elapsed < 0 || elapsed >= (1L << 41)) throw new IllegalStateException("clock is outside the supported ID epoch");
    return (elapsed << (NODE_BITS + SEQUENCE_BITS)) | (nodeId << SEQUENCE_BITS) | sequence;
  }
}
