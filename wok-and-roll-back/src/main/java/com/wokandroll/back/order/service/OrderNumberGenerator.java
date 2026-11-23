package com.wokandroll.back.order.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Order numbers: {@code WR-} and 8 characters of Crockford Base32 from {@link SecureRandom}
 * (api-contract.md §3.9). It is not a sequence, so it cannot be guessed and does not show how many
 * orders there are. The number is unique by the index of {@code customer_order.order_number}; on a
 * conflict the order service asks for a new number.
 */
@Component
public class OrderNumberGenerator {

  static final String PREFIX = "WR-";
  static final int LENGTH = 8;

  /** Crockford Base32: 0-9 and A-Z without I, L, O and U. */
  static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private final SecureRandom random;

  public OrderNumberGenerator() {
    this(new SecureRandom());
  }

  OrderNumberGenerator(SecureRandom random) {
    this.random = random;
  }

  public String next() {
    StringBuilder number = new StringBuilder(PREFIX);
    for (int i = 0; i < LENGTH; i++) {
      number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return number.toString();
  }
}
