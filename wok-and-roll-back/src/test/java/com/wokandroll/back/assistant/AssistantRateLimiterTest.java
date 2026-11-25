package com.wokandroll.back.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AssistantRateLimiterTest {

  /** A clock the test moves by hand. */
  private static final class TestClock extends Clock {
    private Instant now = Instant.parse("2026-11-25T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }

  @Test
  void letsTwentyQuestionsThroughInTenMinutes() {
    TestClock clock = new TestClock();
    AssistantRateLimiter limiter = new AssistantRateLimiter(clock);

    for (int i = 0; i < AssistantRateLimiter.MAX_MESSAGES; i++) {
      assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
      clock.advance(Duration.ofSeconds(10));
    }

    // The first question was 200 s ago: it leaves the window in 400 s.
    assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(400);
    assertThat(limiter.tryAcquire("10.0.0.2")).as("another IP").isZero();
  }

  @Test
  void aRefusedQuestionIsNotCountedAndTheWindowMoves() {
    TestClock clock = new TestClock();
    AssistantRateLimiter limiter = new AssistantRateLimiter(clock);
    for (int i = 0; i < AssistantRateLimiter.MAX_MESSAGES; i++) {
      limiter.tryAcquire("10.0.0.1");
    }
    assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(600);

    clock.advance(AssistantRateLimiter.WINDOW);

    assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
  }
}
