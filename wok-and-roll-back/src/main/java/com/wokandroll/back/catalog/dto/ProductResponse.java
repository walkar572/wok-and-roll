package com.wokandroll.back.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One menu card: a dish with its price in the chosen country (api-contract.md 3.3). */
public record ProductResponse(
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
        boolean hasModifiers) {}
