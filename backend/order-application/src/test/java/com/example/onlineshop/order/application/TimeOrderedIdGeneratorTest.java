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

  private static final class MutableClock extends Clock {
    private Instant instant;
    private MutableClock(Instant instant) { this.instant = instant; }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return instant; }
  }
}
