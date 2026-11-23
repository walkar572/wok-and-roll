package com.wokandroll.back.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.common.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IdempotencyKeysTest {

  @Test
  void noHeaderMeansNoKey() {
    assertThat(IdempotencyKeys.parse(null)).isNull();
    assertThat(IdempotencyKeys.parse("  ")).isNull();
  }

  @Test
  void aUuidIsParsed() {
    UUID key = UUID.randomUUID();

    assertThat(IdempotencyKeys.parse(" " + key + " ")).isEqualTo(key);
  }

  @Test
  void aKeyThatIsNotAUuidIsA400OnTheHeaderField() {
    assertThatThrownBy(() -> IdempotencyKeys.parse("not-a-uuid"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
              assertThat(e.getFields()).containsOnlyKeys("Idempotency-Key");
            });
  }
}
