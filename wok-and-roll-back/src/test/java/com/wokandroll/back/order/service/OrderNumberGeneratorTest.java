package com.wokandroll.back.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

  private final OrderNumberGenerator generator = new OrderNumberGenerator();

  @Test
  void numberIsWrAndEightCrockfordBase32Characters() {
    for (int i = 0; i < 1000; i++) {
      assertThat(generator.next()).matches("WR-[0-9A-HJKMNP-TV-Z]{8}");
    }
  }

  @Test
  void numbersDoNotUseTheLettersILOU() {
    for (int i = 0; i < 1000; i++) {
      assertThat(generator.next().substring(3)).doesNotContainPattern("[ILOU]");
    }
  }

  @Test
  void numbersDiffer() {
    Set<String> numbers = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      numbers.add(generator.next());
    }
    // 32^8 numbers: 1000 draws without a repeat is practically certain
    assertThat(numbers).hasSize(1000);
  }

  @Test
  void alphabetHas32DistinctCharacters() {
    assertThat(OrderNumberGenerator.ALPHABET).hasSize(32);
    assertThat(OrderNumberGenerator.ALPHABET.chars().distinct().count()).isEqualTo(32);
  }
}
