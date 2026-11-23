package com.wokandroll.back.catalog.detail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One modifier of a dish with its price change in the chosen country. */
public record ModifierResponse(
    @Schema(example = "2") long id,
    @Schema(
            example = "SIZE",
            allowableValues = {"SIZE", "EXTRA"})
        String type,
    @Schema(example = "Large") String name,
    @Schema(description = "Local currency, before VAT, 0.00 or more", example = "12.00")
        BigDecimal priceDelta) {}
