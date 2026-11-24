package com.wokandroll.back.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wokandroll.back.catalog.dto.CategoryResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.dto.SetItemResponse;
import com.wokandroll.back.catalog.repository.CatalogRepository;
import com.wokandroll.back.catalog.repository.CatalogRepository.CategoryRow;
import com.wokandroll.back.catalog.repository.CatalogRepository.ProductRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

  private final CatalogRepository repository = mock(CatalogRepository.class);
  private final CatalogService service = new CatalogService(repository);

  private static ProductRow gyoza() {
    return new ProductRow(
        8,
        3,
        "Gyoza",
        "Pan-fried chicken dumplings.",
        "/images/gyoza.png",
        5,
        150,
        null,
        new BigDecimal("30"),
        "GEL",
        true,
        false,
        null);
  }

  @Test
  void mapsCategories() {
    when(repository.findCategories("UZB")).thenReturn(List.of(new CategoryRow(3, "Sides")));

    List<CategoryResponse> result = service.getCategories("UZB");

    assertThat(result).containsExactly(new CategoryResponse(3, "Sides"));
  }

  @Test
  void mapsProductsAndGivesThePriceTwoDecimals() {
    when(repository.findProducts("GEO", 3L)).thenReturn(List.of(gyoza()));

    List<ProductResponse> result = service.getProducts("GEO", 3L);

    assertThat(result).hasSize(1);
    ProductResponse gyoza = result.get(0);
    assertThat(gyoza.id()).isEqualTo(8);
    assertThat(gyoza.categoryId()).isEqualTo(3);
    assertThat(gyoza.imageUrl()).isEqualTo("/images/gyoza.png");
    assertThat(gyoza.pieces()).isEqualTo(5);
    assertThat(gyoza.weightGrams()).isEqualTo(150);
    assertThat(gyoza.volumeMl()).isNull();
    assertThat(gyoza.price().toPlainString()).isEqualTo("30.00");
    assertThat(gyoza.currency()).isEqualTo("GEL");
    assertThat(gyoza.inStock()).isTrue();
    assertThat(gyoza.hasModifiers()).isFalse();
    assertThat(gyoza.regularPrice()).isNull();
    assertThat(gyoza.setItems()).isEmpty();
  }

  @Test
  void aSetGetsItsDishesAndItsRegularPrice() {
    ProductRow set =
        new ProductRow(
            26,
            6,
            "Philadelphia set",
            "Two Philadelphia rolls and a California roll.",
            "/images/set-philadelphia.png",
            24,
            710,
            null,
            new BigDecimal("199000"),
            "UZS",
            true,
            false,
            new BigDecimal("239000"));
    when(repository.findProducts("UZB", null)).thenReturn(List.of(set, gyoza()));
    when(repository.findSetItems())
        .thenReturn(
            Map.of(
                26L,
                List.of(
                    new SetItemResponse(11, "Philadelphia roll", 2),
                    new SetItemResponse(3, "California roll", 1))));

    List<ProductResponse> result = service.getProducts("UZB", null);

    assertThat(result.get(0).regularPrice().toPlainString()).isEqualTo("239000.00");
    assertThat(result.get(0).setItems())
        .extracting(SetItemResponse::name, SetItemResponse::quantity)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Philadelphia roll", 2),
            org.assertj.core.groups.Tuple.tuple("California roll", 1));
    assertThat(result.get(1).setItems()).isEmpty();
  }

  @Test
  void withoutCategoryAsksForAllCategories() {
    service.getProducts("UZB", null);

    verify(repository).findProducts("UZB", null);
  }
}
