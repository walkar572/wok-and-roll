package com.wokandroll.back.auth;

import com.wokandroll.back.auth.dto.AuthResponse;
import com.wokandroll.back.auth.dto.LoginRequest;
import com.wokandroll.back.auth.dto.MeResponse;
import com.wokandroll.back.auth.dto.RegisterRequest;
import com.wokandroll.back.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Register, login and the current user (#62, #151, auth.md section 4). */
@RestController
@Tag(name = "Auth")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a customer and get a token")
  @ApiResponse(responseCode = "201", description = "Registered, role CUSTOMER")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR: bad email, short password, password over 72 bytes, missing name",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "EMAIL_TAKEN",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return service.register(request);
  }

  @PostMapping("/api/v1/auth/login")
  @Operation(summary = "Log in and get a token (24 hours)")
  @ApiResponse(responseCode = "200", description = "Logged in")
  @ApiResponse(
      responseCode = "401",
      description = "INVALID_CREDENTIALS: wrong email or password",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "TOO_MANY_ATTEMPTS: 5 failed logins in 15 minutes, see Retry-After",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return service.login(request, http.getRemoteAddr());
  }

  @GetMapping("/api/v1/me")
  @Operation(
      summary = "The logged-in user",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "Current user")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED: no token, bad token or expired token",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public MeResponse me(@AuthenticationPrincipal AuthUser current) {
    return service.me(current);
  }

  /** A 401 always says how to authenticate (RFC 6750). */
  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException ex) {
    return ResponseEntity.status(ex.getStatus())
        .header(HttpHeaders.WWW_AUTHENTICATE, SecurityErrorHandlers.WWW_AUTHENTICATE_VALUE)
        .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(TooManyAttemptsException.class)
  ResponseEntity<ErrorResponse> tooManyAttempts(TooManyAttemptsException ex) {
    return ResponseEntity.status(ex.getStatus())
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
  }
}
