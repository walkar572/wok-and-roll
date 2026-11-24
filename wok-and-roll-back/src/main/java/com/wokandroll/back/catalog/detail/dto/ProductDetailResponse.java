package com.wokandroll.back.catalog.detail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Product modal: all fields of the product card plus its modifiers (api-contract.md 3.4). */
public record ProductDetailResponse(
    @Schema(example = "5") long id,
    @Schema(example = "2") long categoryId,
    @Schema(example = "Firecracker udon") String name,
    @Schema(example = "Udon noodles, chicken, chili, sesame.", nullable = true) String description,
    @Schema(example = "/images/udon.png", nullable = true) String imageUrl,
    @Schema(
            description = "Pieces in a portion; null when not counted",
            example = "8",
            nullable = true)
        Integer pieces,
    @Schema(description = "Weight of a portion in grams", example = "350", nullable = true)
        Integer weightGrams,
    @Schema(description = "Volume of a drink in millilitres", example = "400", nullable = true)
        Integer volumeMl,
    @Schema(description = "Base price in local currency, before VAT", example = "48.00")
        BigDecimal price,
    @Schema(example = "UZS") String currency,
    @Schema(example = "true") boolean inStock,
    @Schema(description = "true if the dish has at least one modifier priced in this country")
        boolean hasModifiers,
    @Schema(description = "SIZE first, then EXTRA, then id. Empty when there are none")
        List<ModifierResponse> modifiers) {}
