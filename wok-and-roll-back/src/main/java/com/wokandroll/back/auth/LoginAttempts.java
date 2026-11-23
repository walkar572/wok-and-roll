package com.wokandroll.back.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per email and IP (api-contract.md section 3.11, #151). 5 failures in 15
 * minutes block that email from that IP until the oldest failure is 15 minutes old. A successful
 * login resets the counter.
 *
 * <p>The counter lives in memory. That is enough for one backend instance (the MVP); a restart
 * clears it.
 */
@Component
public class LoginAttempts {

  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(15);

  /** Above this many keys, old ones are dropped, so the map cannot grow without limit. */
  private static final int CLEANUP_ABOVE = 10_000;

  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final Clock clock;

  public LoginAttempts() {
    this(Clock.systemUTC());
  }

  LoginAttempts(Clock clock) {
    this.clock = clock;
  }

  /** Seconds to wait before the next try, or 0 when the login may go on. */
  public long retryAfterSeconds(String email, String ip) {
    Deque<Instant> list = failures.get(key(email, ip));
    if (list == null) {
      return 0;
    }
    Instant now = clock.instant();
    synchronized (list) {
      dropOld(list, now);
      if (list.size() < MAX_FAILURES) {
        return 0;
      }
      Duration wait = Duration.between(now, list.peekFirst().plus(WINDOW));
      return Math.max(1, (wait.toMillis() + 999) / 1000);
    }
  }

  public void failed(String email, String ip) {
    Instant now = clock.instant();
    if (failures.size() > CLEANUP_ABOVE) {
      failures.values().removeIf(list -> isOld(list, now));
    }
    Deque<Instant> list = failures.computeIfAbsent(key(email, ip), k -> new ArrayDeque<>());
    synchronized (list) {
      dropOld(list, now);
      list.addLast(now);
    }
  }

  public void succeeded(String email, String ip) {
    failures.remove(key(email, ip));
  }

  private static String key(String email, String ip) {
    return email + "|" + ip;
  }

  private static void dropOld(Deque<Instant> list, Instant now) {
    while (!list.isEmpty() && !list.peekFirst().plus(WINDOW).isAfter(now)) {
      list.removeFirst();
    }
  }

  private static boolean isOld(Deque<Instant> list, Instant now) {
    synchronized (list) {
      return list.isEmpty() || !list.peekLast().plus(WINDOW).isAfter(now);
    }
  }
}
