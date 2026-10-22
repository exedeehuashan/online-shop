package com.example.onlineshop.order.application;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Independent QA coverage for the 63-bit time-ordered ID generator boundaries /
 * 63-bit 时间有序 ID 生成器边界独立 QA 覆盖。
 */
class IdGeneratorQaTest {
  private static final Instant EPOCH = Instant.parse("2024-01-01T00:00:00Z");

  @Test void idsArePositiveStrictlyIncreasingAnd63BitSafeAcrossMilliseconds() {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-22T00:00:00Z"));
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(1023, clock);
    Set<Long> seen = new HashSet<>();
    long previous = 0;
    for (int millisecond = 0; millisecond < 50; millisecond++) {
      for (int index = 0; index < 40; index++) {
        long id = generator.nextId();
        assertThat(id).isPositive().isLessThan(Long.MAX_VALUE).isGreaterThan(previous);
        assertThat(seen.add(id)).as("unique id %s", id).isTrue();
        previous = id;
      }
      clock.instant = clock.instant.plusMillis(1);
    }
  }

  @Test void acceptsTheMaximumNodeAndRejectsOutOfRangeNodes() {
    assertThatCode(() -> new TimeOrderedIdGenerator(1023, Clock.fixed(EPOCH.plusSeconds(60), ZoneOffset.UTC)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> new TimeOrderedIdGenerator(1024, Clock.fixed(EPOCH, ZoneOffset.UTC)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TimeOrderedIdGenerator(-1, Clock.fixed(EPOCH, ZoneOffset.UTC)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void failsClosedWhenTheSameMillisecondSequenceIsExhausted() {
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(1, Clock.fixed(EPOCH.plusSeconds(60), ZoneOffset.UTC));
    for (int index = 0; index < 4096; index++) generator.nextId();

    assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exhausted");
  }

  @Test void rejectsClockBeforeTheIdEpoch() {
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(1, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("epoch");
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) { this.instant = instant; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
