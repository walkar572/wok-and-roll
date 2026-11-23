package com.wokandroll.back.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** The request is wrong from the business point of view (HTTP 400), e.g. quantity below 1. */
public class BadRequestException extends ApiException {

  public BadRequestException(String message) {
    super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
  }

  public BadRequestException(String message, Map<String, String> fields) {
    super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, fields);
  }
}
