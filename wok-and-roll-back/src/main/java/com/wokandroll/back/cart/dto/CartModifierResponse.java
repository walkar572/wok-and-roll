package com.wokandroll.back.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** A chosen modifier of a cart line, with its type and its price change in the local currency. */
public record CartModifierResponse(
    long id,
    String name,
    @Schema(description = "SIZE or EXTRA", example = "SIZE") String type,
    BigDecimal priceDelta) {}
