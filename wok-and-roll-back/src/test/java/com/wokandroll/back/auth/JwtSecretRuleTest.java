package com.wokandroll.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretRuleTest {

  private static final String REAL = "a-real-secret-that-is-long-enough-1234567890";

  @Test
  void usesJwtSecretWhenItIsSet() {
    assertThat(SecurityConfig.resolveSecret(REAL, new MockEnvironment())).isEqualTo(REAL);
  }

  @Test
  void refusesToStartWithoutJwtSecret() {
    assertThatThrownBy(() -> SecurityConfig.resolveSecret("", new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void devProfileFallsBackToTheDevSecret() {
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("dev");
    assertThat(SecurityConfig.resolveSecret("", dev)).isEqualTo(JwtService.DEV_SECRET);
  }

  @Test
  void refusesThePublicDevSecretOutsideDev() {
    assertThatThrownBy(
            () -> SecurityConfig.resolveSecret(JwtService.DEV_SECRET, new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class);
  }
}
