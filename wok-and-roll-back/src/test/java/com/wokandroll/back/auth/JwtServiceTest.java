package com.wokandroll.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  /** 64 bytes, so the same key could also sign HS384 and HS512 tokens. */
  private static final String SECRET =
      "test-secret-that-is-long-enough-for-hs256-and-even-for-hs512-ok!";

  private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  private static final AuthUser AZIZ = new AuthUser(15, "aziz@mail.uz", "CUSTOMER");

  private static JwtService at(Instant instant) {
    return new JwtService(SECRET, 24, Clock.fixed(instant, ZoneOffset.UTC));
  }

  /** A token like ours, but with the parts a test wants to change. */
  private static String token(String issuer, String audience, String role) {
    var builder =
        Jwts.builder()
            .subject("15")
            .claim("email", "aziz@mail.uz")
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plus(Duration.ofHours(1))));
    if (issuer != null) {
      builder.issuer(issuer);
    }
    if (audience != null) {
      builder.audience().add(audience).and();
    }
    if (role != null) {
      builder.claim("role", role);
    }
    return builder.signWith(KEY, Jwts.SIG.HS256).compact();
  }

  @Test
  void tokenCarriesIdEmailAndRole() {
    JwtService jwt = at(NOW);
    assertThat(jwt.parse(jwt.createToken(AZIZ))).contains(AZIZ);
  }

  @Test
  void tokenHasOurIssuerAndAudience() {
    String token = at(NOW).createToken(AZIZ);
    var claims = Jwts.parser().verifyWith(KEY).clock(() -> Date.from(NOW)).build();
    var payload = claims.parseSignedClaims(token).getPayload();
    assertThat(payload.getIssuer()).isEqualTo("wok-and-roll");
    assertThat(payload.getAudience()).containsExactly("wok-and-roll-web");
  }

  @Test
  void tokenExpiresAfter24Hours() {
    String token = at(NOW).createToken(AZIZ);
    assertThat(at(NOW.plus(Duration.ofHours(23))).parse(token)).isPresent();
    assertThat(at(NOW.plus(Duration.ofHours(25))).parse(token)).isEmpty();
  }

  @Test
  void wrongOrMissingIssuerOrAudienceIsRejected() {
    JwtService jwt = at(NOW);
    assertThat(jwt.parse(token("wok-and-roll", "wok-and-roll-web", "CUSTOMER"))).isPresent();
    assertThat(jwt.parse(token("someone-else", "wok-and-roll-web", "CUSTOMER"))).isEmpty();
    assertThat(jwt.parse(token("wok-and-roll", "another-app", "CUSTOMER"))).isEmpty();
    assertThat(jwt.parse(token(null, "wok-and-roll-web", "CUSTOMER"))).isEmpty();
    assertThat(jwt.parse(token("wok-and-roll", null, "CUSTOMER"))).isEmpty();
  }

  @Test
  void tokenWithoutRoleIsRejected() {
    assertThat(at(NOW).parse(token("wok-and-roll", "wok-and-roll-web", null))).isEmpty();
  }

  @Test
  void onlyHs256IsAccepted() {
    String hs512 =
        Jwts.builder()
            .issuer("wok-and-roll")
            .audience()
            .add("wok-and-roll-web")
            .and()
            .subject("15")
            .claim("role", "ADMIN")
            .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
            .signWith(KEY, Jwts.SIG.HS512)
            .compact();
    assertThat(at(NOW).parse(hs512)).isEmpty();
  }

  @Test
  void tokenSignedWithAnotherSecretIsRejected() {
    String foreign =
        new JwtService(
                "another-secret-that-is-also-long-enough", 24, Clock.fixed(NOW, ZoneOffset.UTC))
            .createToken(AZIZ);
    assertThat(at(NOW).parse(foreign)).isEmpty();
  }

  @Test
  void brokenTokenIsRejected() {
    assertThat(at(NOW).parse("not-a-jwt")).isEmpty();
    assertThat(at(NOW).parse("")).isEmpty();
  }

  @Test
  void shortSecretStopsTheApp() {
    assertThatThrownBy(() -> new JwtService("too-short", 24))
        .isInstanceOf(IllegalStateException.class);
  }
}
