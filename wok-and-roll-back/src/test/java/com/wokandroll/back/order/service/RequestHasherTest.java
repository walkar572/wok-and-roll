package com.wokandroll.back.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wokandroll.back.cart.dto.CartItemRequest;
import com.wokandroll.back.order.dto.OrderRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

  private final RequestHasher hasher = new RequestHasher();

  @Test
  void sameRequestGivesTheSameHashOf64HexCharacters() {
    String hash = hasher.hash(request("Aziz Karimov", 2));

    assertThat(hash).matches("[0-9a-f]{64}");
    assertThat(hasher.hash(request("Aziz Karimov", 2))).isEqualTo(hash);
  }

  @Test
  void anyChangeOfTheRequestChangesTheHash() {
    String hash = hasher.hash(request("Aziz Karimov", 2));

    assertThat(hasher.hash(request("Aziz Karimova", 2))).isNotEqualTo(hash);
    assertThat(hasher.hash(request("Aziz Karimov", 3))).isNotEqualTo(hash);
  }

  private static OrderRequest request(String contactName, int quantity) {
    return new OrderRequest(
        "UZB",
        "2026-11-20",
        "PICKUP",
        1L,
        null,
        null,
        "12:30",
        contactName,
        "+998901234567",
        null,
        null,
        new BigDecimal("209.63"),
        List.of(new CartItemRequest(5L, BigDecimal.valueOf(quantity), List.of())));
  }
}
