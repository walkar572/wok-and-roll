package com.wokandroll.back.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and checks JWT tokens. The secret comes from the env variable JWT_SECRET and must be at
 * least 32 bytes. Token life: JWT_TTL_HOURS, default 24.
 *
 * <p>A token is accepted only if it is HS256, signed with our key, not expired, and has {@code iss}
 * {@value #ISSUER} and {@code aud} {@value #AUDIENCE} (api-contract.md section 3.11, #151).
 */
public class JwtService {

  static final String DEV_SECRET = "dev-only-secret-change-me-it-is-not-for-the-demo";
  static final String ISSUER = "wok-and-roll";
  static final String AUDIENCE = "wok-and-roll-web";
  static final String ALGORITHM = "HS256";

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  private final SecretKey key;
  private final Duration ttl;
  private final Clock clock;

  public JwtService(String secret, long ttlHours) {
    this(secret, ttlHours, Clock.systemUTC());
  }

  JwtService(String secret, long ttlHours, Clock clock) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
    }
    if (DEV_SECRET.equals(secret)) {
      log.warn("dev profile: using the public development secret. Never use it outside local dev.");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofHours(ttlHours);
    this.clock = clock;
  }

  public String createToken(AuthUser user) {
    Instant now = clock.instant();
    return Jwts.builder()
        .issuer(ISSUER)
        .audience()
        .add(AUDIENCE)
        .and()
        .subject(String.valueOf(user.id()))
        .claim("email", user.email())
        .claim("role", user.role())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * Empty when the token is broken, signed with another key or algorithm, expired, or has the wrong
   * issuer or audience.
   */
  public Optional<AuthUser> parse(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(ISSUER)
              .requireAudience(AUDIENCE)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token);
      // our key is long enough for HS384 and HS512 too, so the algorithm is checked by hand
      if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
        return Optional.empty();
      }
      Claims claims = jws.getPayload();
      String role = claims.get("role", String.class);
      if (claims.getSubject() == null || role == null) {
        return Optional.empty();
      }
      return Optional.of(
          new AuthUser(
              Long.parseLong(claims.getSubject()), claims.get("email", String.class), role));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
