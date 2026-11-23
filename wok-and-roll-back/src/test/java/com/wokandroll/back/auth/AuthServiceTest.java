package com.wokandroll.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wokandroll.back.auth.AppUserRepository.UserRow;
import com.wokandroll.back.auth.dto.AuthResponse;
import com.wokandroll.back.auth.dto.LoginRequest;
import com.wokandroll.back.auth.dto.RegisterRequest;
import com.wokandroll.back.common.error.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private static final String IP = "10.0.0.1";

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast for tests
  private final JwtService jwt = new JwtService("test-secret-that-is-long-enough-for-hs256", 24);
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T10:00:00Z"));
  private final AuthService service =
      new AuthService(users, encoder, jwt, new LoginAttempts(clock));

  private void azizExists(String role) {
    when(users.findByEmail("aziz@mail.uz"))
        .thenReturn(
            Optional.of(
                new UserRow(15, "aziz@mail.uz", encoder.encode("secret123"), "Aziz", null, role)));
  }

  private void wrongLogin() {
    assertThatThrownBy(() -> service.login(new LoginRequest("aziz@mail.uz", "wrong-pass"), IP))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void registerSavesLowerCaseEmailAndHashedPasswordAndGivesCustomer() {
    when(users.existsByEmail("aziz@mail.uz")).thenReturn(false);
    when(users.insert(anyString(), anyString(), anyString(), isNull(), eq("CUSTOMER")))
        .thenReturn(15L);

    AuthResponse result =
        service.register(new RegisterRequest(" Aziz@Mail.UZ ", "secret123", "Aziz Karimov", " "));

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(users)
        .insert(eq("aziz@mail.uz"), hash.capture(), eq("Aziz Karimov"), isNull(), eq("CUSTOMER"));
    assertThat(hash.getValue()).isNotEqualTo("secret123");
    assertThat(encoder.matches("secret123", hash.getValue())).isTrue();
    assertThat(result.role()).isEqualTo("CUSTOMER");
    assertThat(jwt.parse(result.token())).contains(new AuthUser(15, "aziz@mail.uz", "CUSTOMER"));
  }

  @Test
  void registerWithTakenEmailIsConflict() {
    when(users.existsByEmail("aziz@mail.uz")).thenReturn(true);

    assertThatThrownBy(
            () -> service.register(new RegisterRequest("aziz@mail.uz", "secret123", "Aziz", null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("EMAIL_TAKEN"));
    verify(users, never()).insert(any(), any(), any(), any(), any());
  }

  @Test
  void passwordOver72BytesIs400NotA500() {
    String cyrillic = "пароль".repeat(7); // 42 characters, 84 bytes

    assertThatThrownBy(
            () -> service.register(new RegisterRequest("aziz@mail.uz", cyrillic, "Aziz", null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus().value()).isEqualTo(400);
              assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
              assertThat(e.getFields()).containsKey("password");
            });
    verify(users, never()).insert(any(), any(), any(), any(), any());
  }

  @Test
  void passwordOf72BytesIsFine() {
    assertThat(AuthService.fitsBcrypt("a".repeat(72))).isTrue();
    assertThat(AuthService.fitsBcrypt("a".repeat(71) + "ж")).isFalse(); // 73 bytes
  }

  @Test
  void loginWithRightPasswordGivesToken() {
    azizExists("VIEWER");

    AuthResponse result = service.login(new LoginRequest("AZIZ@mail.uz", "secret123"), IP);

    assertThat(result.role()).isEqualTo("VIEWER");
    assertThat(jwt.parse(result.token())).map(AuthUser::id).contains(15L);
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveTheSameError() {
    azizExists("CUSTOMER");
    when(users.findByEmail("nobody@mail.uz")).thenReturn(Optional.empty());

    for (LoginRequest bad :
        new LoginRequest[] {
          new LoginRequest("aziz@mail.uz", "wrong-password"),
          new LoginRequest("nobody@mail.uz", "secret123")
        }) {
      assertThatThrownBy(() -> service.login(bad, IP))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS");
                assertThat(e.getMessage()).isEqualTo("Email or password is wrong");
              });
    }
  }

  @Test
  void longPasswordAtLoginIsJustWrongNotA500() {
    azizExists("CUSTOMER");
    assertThatThrownBy(() -> service.login(new LoginRequest("aziz@mail.uz", "я".repeat(40)), IP))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void sixthLoginAfterFiveFailuresIs429EvenWithTheRightPassword() {
    azizExists("CUSTOMER");
    for (int i = 0; i < 5; i++) {
      wrongLogin();
    }

    assertThatThrownBy(() -> service.login(new LoginRequest("aziz@mail.uz", "secret123"), IP))
        .isInstanceOfSatisfying(
            TooManyAttemptsException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
              assertThat(e.getStatus().value()).isEqualTo(429);
              assertThat(e.getRetryAfterSeconds()).isEqualTo(15 * 60);
            });

    clock.advance(Duration.ofMinutes(15));
    assertThat(service.login(new LoginRequest("aziz@mail.uz", "secret123"), IP).role())
        .isEqualTo("CUSTOMER");
  }

  @Test
  void aGoodLoginResetsTheCounter() {
    azizExists("CUSTOMER");
    for (int i = 0; i < 4; i++) {
      wrongLogin();
    }
    service.login(new LoginRequest("aziz@mail.uz", "secret123"), IP);
    for (int i = 0; i < 4; i++) {
      wrongLogin();
    }
    assertThat(service.login(new LoginRequest("aziz@mail.uz", "secret123"), IP).role())
        .isEqualTo("CUSTOMER");
  }
}
