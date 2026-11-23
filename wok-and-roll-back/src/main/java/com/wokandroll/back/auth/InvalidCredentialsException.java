package com.wokandroll.back.auth;

import com.wokandroll.back.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Login: wrong email or wrong password. Both give the same answer on purpose. */
public class InvalidCredentialsException extends ApiException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is wrong");
  }
}
