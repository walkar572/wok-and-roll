package com.wokandroll.back.catalog.detail;

import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import com.wokandroll.back.catalog.detail.dto.ProductDetailResponse;
import com.wokandroll.back.common.error.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** Builds the product modal: the dish in the chosen country and its modifiers. */
@Service
public class ProductDetailService {

  private final ProductDetailRepository repository;

  public ProductDetailService(ProductDetailRepository repository) {
    this.repository = repository;
  }

  public ProductDetailResponse getProduct(long productId, String countryCode) {
    ProductDetailRepository.ProductRow row =
        repository
            .findProduct(productId, countryCode)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Product " + productId + " is not sold in " + countryCode));

    List<ModifierResponse> modifiers =
        repository.findModifiers(productId, countryCode).stream()
            .map(m -> new ModifierResponse(m.id(), m.type(), m.name(), money(m.priceDelta())))
            .toList();

    return new ProductDetailResponse(
        row.id(),
        row.categoryId(),
        row.name(),
        row.description(),
        row.imageUrl(),
        money(row.price()),
        row.currency(),
        row.inStock(),
        !modifiers.isEmpty(),
        modifiers);
  }

  /** Money always has exactly 2 decimals in the JSON (api-contract.md section 1). */
  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
