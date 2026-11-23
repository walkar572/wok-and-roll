package com.wokandroll.back.auth;

import com.wokandroll.back.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** 5 failed logins in 15 minutes. The controller adds the header Retry-After. */
public class TooManyAttemptsException extends ApiException {

  private final long retryAfterSeconds;

  public TooManyAttemptsException(long retryAfterSeconds) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "TOO_MANY_ATTEMPTS",
        "Too many failed logins. Try again in " + minutes(retryAfterSeconds) + " min");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  private static long minutes(long seconds) {
    return Math.max(1, (seconds + 59) / 60);
  }
}
