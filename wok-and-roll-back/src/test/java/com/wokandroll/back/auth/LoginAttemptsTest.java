package com.wokandroll.back.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginAttemptsTest {

  private static final String EMAIL = "aziz@mail.uz";
  private static final String IP = "10.0.0.1";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T10:00:00Z"));
  private final LoginAttempts attempts = new LoginAttempts(clock);

  private void fail(int times) {
    for (int i = 0; i < times; i++) {
      attempts.failed(EMAIL, IP);
      clock.advance(Duration.ofMinutes(1));
    }
  }

  @Test
  void fourFailuresDoNotBlock() {
    fail(4);
    assertThat(attempts.retryAfterSeconds(EMAIL, IP)).isZero();
  }

  @Test
  void fiveFailuresIn15MinutesBlockUntilTheOldestIs15MinutesOld() {
    fail(5); // at 10:00, 10:01, 10:02, 10:03, 10:04; now it is 10:05
    assertThat(attempts.retryAfterSeconds(EMAIL, IP)).isEqualTo(10 * 60);

    clock.advance(Duration.ofMinutes(10)); // 10:15, the first failure is 15 minutes old
    assertThat(attempts.retryAfterSeconds(EMAIL, IP)).isZero();
  }

  @Test
  void theCounterIsPerEmailAndIp() {
    fail(5);
    assertThat(attempts.retryAfterSeconds(EMAIL, "10.0.0.2")).isZero();
    assertThat(attempts.retryAfterSeconds("other@mail.uz", IP)).isZero();
  }

  @Test
  void aSuccessfulLoginResetsTheCounter() {
    fail(4);
    attempts.succeeded(EMAIL, IP);
    fail(4);
    assertThat(attempts.retryAfterSeconds(EMAIL, IP)).isZero();
  }

  @Test
  void failuresOlderThan15MinutesDoNotCount() {
    fail(4);
    clock.advance(Duration.ofMinutes(20));
    fail(1);
    assertThat(attempts.retryAfterSeconds(EMAIL, IP)).isZero();
  }
}
