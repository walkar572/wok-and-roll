package com.wokandroll.back.common.error;

import org.springframework.http.HttpStatus;

/** The requested object does not exist (HTTP 404). */
public class NotFoundException extends ApiException {

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
