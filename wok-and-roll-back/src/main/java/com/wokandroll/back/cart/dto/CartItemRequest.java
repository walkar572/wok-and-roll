package com.wokandroll.back.cart.dto;

import com.wokandroll.back.cart.validation.WholeNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** One cart line: a product, how many, and the chosen modifiers. */
@Schema(description = "One cart line")
public record CartItemRequest(
    @Schema(example = "7") @NotNull(message = "is required") Long productId,
    @Schema(type = "integer", example = "2", minimum = "1", description = "Whole number, 1 to 99")
        @NotNull(message = "is required")
        @WholeNumber
        BigDecimal quantity,
    @Schema(description = "Chosen modifiers of this product. Can be empty or missing.")
        List<@NotNull(message = "must not be null") Long> modifierIds) {}
