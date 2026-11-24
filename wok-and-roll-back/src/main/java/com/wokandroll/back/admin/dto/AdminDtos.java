package com.wokandroll.back.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Request and response bodies of the admin catalog API (api-contract.md section 3.15, #178). */
public final class AdminDtos {

  private AdminDtos() {}

  /** A product with its price in one country; the price fields are null when not sold there. */
  public record AdminProductResponse(
      @Schema(example = "5") long id,
      @Schema(example = "2") long categoryId,
      @Schema(example = "From the wok") String categoryName,
      @Schema(example = "Firecracker udon") String name,
      @Schema(example = "Udon noodles, chicken, chili, sesame.") String description,
      @Schema(example = "/images/udon.png") String imageUrl,
      @Schema(example = "8", nullable = true) Integer pieces,
      @Schema(example = "350", nullable = true) Integer weightGrams,
      @Schema(example = "400", nullable = true) Integer volumeMl,
      @Schema(example = "true") boolean visible,
      @Schema(example = "0") int version,
      @Schema(example = "UZB") String country,
      @Schema(example = "48.00", nullable = true) BigDecimal price,
      @Schema(example = "UZS") String currency,
      @Schema(example = "true", nullable = true) Boolean inStock,
      @Schema(example = "0", nullable = true) Integer priceVersion) {}

  /** POST /admin/products. */
  public record CreateProductRequest(
      @Schema(example = "2") Long categoryId,
      @Schema(example = "Spicy ramen") String name,
      @Schema(example = "Ramen noodles, pork, egg, chili.") String description,
      @Schema(example = "/images/udon.png", description = "Must start with /images/")
          String imageUrl,
      @Schema(example = "8", description = "Pieces in a portion, 1-999; empty: not counted")
          Integer pieces,
      @Schema(example = "350", description = "Weight of a portion in grams, 1-9999")
          Integer weightGrams,
      @Schema(example = "400", description = "Volume of a drink in millilitres, 1-9999")
          Integer volumeMl,
      @Schema(example = "true") Boolean visible) {}

  /**
   * PATCH /admin/products/{id}: only the sent fields change. An empty description or image URL
   * removes it, and so does 0 for pieces, weightGrams or volumeMl. {@code version} is required.
   */
  public record UpdateProductRequest(
      Long categoryId,
      String name,
      String description,
      String imageUrl,
      Integer pieces,
      Integer weightGrams,
      Integer volumeMl,
      Boolean visible,
      @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED) Integer version) {}

  /** PUT /admin/products/{id}/prices/{country}. {@code version} is ignored for a new price. */
  public record PriceRequest(
      @Schema(example = "48.00") BigDecimal price,
      @Schema(example = "true") Boolean inStock,
      @Schema(example = "0") Integer version) {}

  public record PriceResponse(
      @Schema(example = "5") long productId,
      @Schema(example = "UZB") String country,
      @Schema(example = "48.00") BigDecimal price,
      @Schema(example = "UZS") String currency,
      @Schema(example = "true") boolean inStock,
      @Schema(example = "1") int version) {}

  /** POST /admin/categories. */
  public record CreateCategoryRequest(
      @Schema(example = "Soups") String name,
      @Schema(example = "4") Integer sortOrder,
      @Schema(example = "true") Boolean visible) {}

  /** PATCH /admin/categories/{id}: only the sent fields change; {@code version} is required. */
  public record UpdateCategoryRequest(
      String name, Integer sortOrder, Boolean visible, Integer version) {}

  public record CategoryResponse(
      @Schema(example = "4") long id,
      @Schema(example = "Soups") String name,
      @Schema(example = "4") int sortOrder,
      @Schema(example = "true") boolean visible,
      @Schema(example = "0") int version) {}
}
