package com.wokandroll.back.catalog.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wokandroll.back.catalog.detail.ProductDetailRepository.ProductRow;
import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import com.wokandroll.back.catalog.detail.dto.ProductDetailResponse;
import com.wokandroll.back.common.error.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductDetailServiceTest {

  private final ProductDetailRepository repository = mock(ProductDetailRepository.class);
  private final ProductDetailService service = new ProductDetailService(repository);

  private static ProductRow udon() {
    return new ProductRow(
        5,
        2,
        "Firecracker udon",
        "Udon noodles, chicken, chili, sesame.",
        "/images/udon.png",
        null,
        350,
        null,
        new BigDecimal("48.00"),
        "UZS",
        true);
  }

  @Test
  void returnsDishWithModifiers() {
    when(repository.findProduct(5, "UZB")).thenReturn(Optional.of(udon()));
    when(repository.findModifiers(5, "UZB"))
        .thenReturn(
            List.of(
                new ModifierResponse(1, "SIZE", "Regular", new BigDecimal("0")),
                new ModifierResponse(2, "SIZE", "Large", new BigDecimal("12.00"))));

    ProductDetailResponse result = service.getProduct(5, "UZB");

    assertThat(result.id()).isEqualTo(5);
    assertThat(result.price()).isEqualByComparingTo("48.00");
    assertThat(result.currency()).isEqualTo("UZS");
    assertThat(result.hasModifiers()).isTrue();
    assertThat(result.modifiers())
        .extracting(ModifierResponse::name)
        .containsExactly("Regular", "Large");
    // money always has 2 decimals, even 0
    assertThat(result.modifiers().get(0).priceDelta().scale()).isEqualTo(2);
    assertThat(result.modifiers().get(0).priceDelta().toPlainString()).isEqualTo("0.00");
  }

  @Test
  void dishWithoutModifiersHasEmptyListAndFlagFalse() {
    when(repository.findProduct(7, "KAZ")).thenReturn(Optional.of(udon()));
    when(repository.findModifiers(7, "KAZ")).thenReturn(List.of());

    ProductDetailResponse result = service.getProduct(7, "KAZ");

    assertThat(result.hasModifiers()).isFalse();
    assertThat(result.modifiers()).isEmpty();
  }

  @Test
  void unknownOrNotSoldDishIsNotFound() {
    when(repository.findProduct(99, "UZB")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getProduct(99, "UZB"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Product 99 is not sold in UZB");
  }
}
