package com.wokandroll.back.auth;

import com.wokandroll.back.auth.dto.AuthResponse;
import com.wokandroll.back.auth.dto.LoginRequest;
import com.wokandroll.back.auth.dto.MeResponse;
import com.wokandroll.back.auth.dto.RegisterRequest;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Register, login and "who am I". Design: wok-and-roll-back/docs/auth.md. */
@Service
public class AuthService {

  static final String CUSTOMER = "CUSTOMER";

  /** BCrypt reads at most 72 bytes. A Cyrillic letter is 2 bytes, an emoji 4. */
  static final int MAX_PASSWORD_BYTES = 72;

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginAttempts attempts;

  /** Checked when the email is unknown, so a wrong email takes as long as a wrong password. */
  private final String dummyHash;

  public AuthService(
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      LoginAttempts attempts) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.attempts = attempts;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  public AuthResponse register(RegisterRequest request) {
    if (!fitsBcrypt(request.password())) {
      // 8 to 72 characters can still be more than 72 bytes; BCrypt would throw and give a 500
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("password", "is too long: at most 72 bytes (letters outside English count as 2)"));
    }
    String email = normalize(request.email());
    if (users.existsByEmail(email)) {
      throw emailTaken();
    }
    long id;
    try {
      id =
          users.insert(
              email,
              passwordEncoder.encode(request.password()),
              request.fullName().trim(),
              blankToNull(request.phone()),
              CUSTOMER);
    } catch (DuplicateKeyException e) {
      throw emailTaken(); // two registrations with the same email at the same moment
    }
    return token(new AuthUser(id, email, CUSTOMER));
  }

  /**
   * @param clientIp the caller's IP; together with the email it is the key of the failed-login
   *     counter
   */
  public AuthResponse login(LoginRequest request, String clientIp) {
    String email = normalize(request.email());
    long wait = attempts.retryAfterSeconds(email, clientIp);
    if (wait > 0) {
      throw new TooManyAttemptsException(wait);
    }
    var user = users.findByEmail(email);
    String hash = user.map(AppUserRepository.UserRow::passwordHash).orElse(dummyHash);
    // a password longer than 72 bytes can never be right, so BCrypt is not asked at all
    boolean passwordOk =
        fitsBcrypt(request.password()) && passwordEncoder.matches(request.password(), hash);
    if (user.isEmpty() || !passwordOk) {
      // the same answer for a wrong email and a wrong password, so nobody can check which emails
      // exist
      attempts.failed(email, clientIp);
      throw new InvalidCredentialsException();
    }
    attempts.succeeded(email, clientIp);
    var row = user.get();
    return token(new AuthUser(row.id(), row.email(), row.role()));
  }

  public MeResponse me(AuthUser current) {
    var row =
        users.findById(current.id()).orElseThrow(() -> new NotFoundException("User not found"));
    return new MeResponse(row.id(), row.email(), row.fullName(), row.phone(), row.role());
  }

  private AuthResponse token(AuthUser user) {
    return new AuthResponse(jwtService.createToken(user), user.role());
  }

  private static ApiException emailTaken() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "EMAIL_TAKEN",
        "This email is already registered",
        Map.of("email", "is already registered"));
  }

  static boolean fitsBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
  }

  static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
