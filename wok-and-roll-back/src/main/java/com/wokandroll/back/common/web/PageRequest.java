package com.wokandroll.back.common.web;

import com.wokandroll.back.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Paging of the lists that can grow (api-contract.md section 1.1): {@code
 * ?pageSize=20&pageToken=…}. The token is opaque for clients. Today it is base64url of {@code
 * offset:N}; it can become a keyset later without breaking clients.
 */
public record PageRequest(int pageSize, int offset) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;
  private static final String PREFIX = "offset:";

  /**
   * Reads the two query parameters. A wrong size or a broken token is a 400 VALIDATION_ERROR with
   * the field pageSize or pageToken.
   */
  public static PageRequest of(Integer pageSize, String pageToken) {
    int size = pageSize == null ? DEFAULT_SIZE : pageSize;
    if (size < 1 || size > MAX_SIZE) {
      throw invalid("pageSize", "must be from 1 to " + MAX_SIZE);
    }
    return new PageRequest(size, decode(pageToken));
  }

  /** The token of the next page, or null when this page is the last one. */
  public String nextToken(int returned, boolean hasMore) {
    return hasMore ? encode(offset + returned) : null;
  }

  static String encode(int offset) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((PREFIX + offset).getBytes(StandardCharsets.UTF_8));
  }

  static int decode(String token) {
    if (token == null || token.isBlank()) {
      return 0;
    }
    try {
      String text = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      if (!text.startsWith(PREFIX)) {
        throw invalid("pageToken", "is not a token of this list");
      }
      int offset = Integer.parseInt(text.substring(PREFIX.length()));
      if (offset < 0) {
        throw invalid("pageToken", "is not a token of this list");
      }
      return offset;
    } catch (IllegalArgumentException e) { // also NumberFormatException
      throw invalid("pageToken", "is not a token of this list");
    }
  }

  private static ApiException invalid(String field, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Request validation failed",
        Map.of(field, message));
  }
}
