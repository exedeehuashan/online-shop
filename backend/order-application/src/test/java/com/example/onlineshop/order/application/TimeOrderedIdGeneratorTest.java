package com.example.onlineshop.order.application;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class TimeOrderedIdGeneratorTest {
  @Test void generatesPositiveIncreasing63BitIdsWithinOneMillisecond() {
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(7, Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC));
    long first = generator.nextId();
    long second = generator.nextId();
    assertThat(first).isPositive();
    assertThat(second).isGreaterThan(first);
  }

  @Test void failsClosedWhenClockMovesBackwards() {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-22T00:00:01Z"));
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(1, clock);
    generator.nextId();
    clock.instant = clock.instant.minusMillis(1);
    assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class).hasMessageContaining("backwards");
  }

  @Test void supportsTheLargestPositive63BitTimestampBoundary() {
    Instant lastSupportedMillis = Instant.parse("2024-01-01T00:00:00Z").plusMillis((1L << 41) - 1);
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(1023,
        Clock.fixed(lastSupportedMillis, ZoneOffset.UTC));

    assertThat(generator.nextId()).isPositive().isLessThanOrEqualTo(Long.MAX_VALUE);
    assertThatThrownBy(() -> new TimeOrderedIdGenerator(0,
        Clock.fixed(lastSupportedMillis.plusMillis(1), ZoneOffset.UTC)).nextId())
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside");
  }

  @Test void reservesZeroAtEpochWithoutCreatingADuplicate() {
    TimeOrderedIdGenerator generator = new TimeOrderedIdGenerator(0,
        Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(generator.nextId()).isEqualTo(1);
    assertThat(generator.nextId()).isEqualTo(2);
    for (int expected = 3; expected <= 4095; expected++) assertThat(generator.nextId()).isEqualTo(expected);
    assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sequence exhausted");
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private MutableClock(Instant instant) { this.instant = instant; }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return instant; }
  }
}
