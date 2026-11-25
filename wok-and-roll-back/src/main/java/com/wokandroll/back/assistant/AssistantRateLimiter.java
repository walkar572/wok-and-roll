package com.wokandroll.back.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * At most 20 questions per IP in 10 minutes (api-contract.md section 3.21), so one visitor cannot
 * run up the bill of the API key. In memory, like the login counter: enough for one backend.
 */
@Component
public class AssistantRateLimiter {

  static final int MAX_MESSAGES = 20;
  static final Duration WINDOW = Duration.ofMinutes(10);

  /** Above this many IPs, old ones are dropped, so the map cannot grow without limit. */
  private static final int CLEANUP_ABOVE = 10_000;

  private final Map<String, Deque<Instant>> sent = new ConcurrentHashMap<>();
  private final Clock clock;

  public AssistantRateLimiter() {
    this(Clock.systemUTC());
  }

  AssistantRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * Counts one question of this IP: 0 when it may go on, otherwise the seconds until the oldest
   * question of the window is old enough (and the question is not counted).
   */
  public long tryAcquire(String ip) {
    Instant now = clock.instant();
    if (sent.size() > CLEANUP_ABOVE) {
      sent.values().removeIf(list -> isOld(list, now));
    }
    Deque<Instant> list = sent.computeIfAbsent(ip, key -> new ArrayDeque<>());
    synchronized (list) {
      while (!list.isEmpty() && !list.peekFirst().plus(WINDOW).isAfter(now)) {
        list.removeFirst();
      }
      if (list.size() >= MAX_MESSAGES) {
        Duration wait = Duration.between(now, list.peekFirst().plus(WINDOW));
        return Math.max(1, (wait.toMillis() + 999) / 1000);
      }
      list.addLast(now);
      return 0;
    }
  }

  private static boolean isOld(Deque<Instant> list, Instant now) {
    synchronized (list) {
      return list.isEmpty() || !list.peekLast().plus(WINDOW).isAfter(now);
    }
  }
}
