package com.wokandroll.back.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wokandroll.back.catalog.dto.CategoryResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.repository.CatalogRepository;
import com.wokandroll.back.catalog.repository.CatalogRepository.CategoryRow;
import com.wokandroll.back.catalog.repository.CatalogRepository.ProductRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

  private final CatalogRepository repository = mock(CatalogRepository.class);
  private final CatalogService service = new CatalogService(repository);

  private static ProductRow gyoza() {
    return new ProductRow(
        8,
        3,
        "Gyoza",
        "Pan-fried chicken dumplings. 5 pieces.",
        "/images/gyoza.png",
        new BigDecimal("30"),
        "GEL",
        true,
        false);
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
    assertThat(gyoza.price().toPlainString()).isEqualTo("30.00");
    assertThat(gyoza.currency()).isEqualTo("GEL");
    assertThat(gyoza.inStock()).isTrue();
    assertThat(gyoza.hasModifiers()).isFalse();
  }

  @Test
  void withoutCategoryAsksForAllCategories() {
    service.getProducts("UZB", null);

    verify(repository).findProducts("UZB", null);
  }
}
