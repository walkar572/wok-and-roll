package com.wokandroll.back.catalog.service;

import com.wokandroll.back.catalog.dto.CategoryResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.dto.SetItemResponse;
import com.wokandroll.back.catalog.repository.CatalogRepository;
import com.wokandroll.back.catalog.repository.CatalogRepository.ProductRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** The menu of one country (#51): categories for the filter and dishes for the cards. */
@Service
public class CatalogService {

  private final CatalogRepository repository;

  public CatalogService(CatalogRepository repository) {
    this.repository = repository;
  }

  public List<CategoryResponse> getCategories(String countryCode) {
    return repository.findCategories(countryCode).stream()
        .map(c -> new CategoryResponse(c.id(), c.name()))
        .toList();
  }

  /** A null categoryId means all categories. An unknown id gives an empty list. */
  public List<ProductResponse> getProducts(String countryCode, Long categoryId) {
    List<ProductRow> rows = repository.findProducts(countryCode, categoryId);
    Map<Long, List<SetItemResponse>> setItems = repository.findSetItems();
    Map<Long, DishFacts.Facts> facts = DishFacts.of(repository.findDishTexts(), setItems);
    return rows.stream()
        .map(
            row -> toResponse(row, setItems.getOrDefault(row.id(), List.of()), facts.get(row.id())))
        .toList();
  }

  private static ProductResponse toResponse(
      ProductRow row, List<SetItemResponse> setItems, DishFacts.Facts facts) {
    return new ProductResponse(
        row.id(),
        row.categoryId(),
        row.name(),
        row.description(),
        row.imageUrl(),
        row.pieces(),
        row.weightGrams(),
        row.volumeMl(),
        money(row.price()),
        row.currency(),
        row.inStock(),
        row.hasModifiers(),
        row.regularPrice() == null ? null : money(row.regularPrice()),
        List.copyOf(setItems),
        facts != null && facts.vegetarian(),
        facts != null && facts.spicy());
  }

  /** Money always has exactly 2 decimals in the JSON (api-contract.md section 1). */
  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
