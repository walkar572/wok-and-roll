package com.wokandroll.back.order.service;

import com.wokandroll.back.common.error.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Reads the {@code Idempotency-Key} header (api-contract.md §3.9). */
public final class IdempotencyKeys {

  public static final String HEADER = "Idempotency-Key";

  private IdempotencyKeys() {}

  /**
   * Returns the UUID of the header, or null if there is no header: the request still works, a retry
   * of it is a new order (api-contract.md 3.9). {@code customer_order.idempotency_key} accepts
   * null.
   */
  public static UUID parse(String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(header.trim());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Invalid Idempotency-Key",
          Map.of(HEADER, "must be a UUID"));
    }
  }
}
