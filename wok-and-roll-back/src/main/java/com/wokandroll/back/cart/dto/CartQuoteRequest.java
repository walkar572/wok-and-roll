package com.wokandroll.back.cart.dto;

import com.wokandroll.back.pricing.service.PricingLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request of {@code POST /api/v1/cart/quote}. There are no prices: the backend knows them. */
@Schema(description = "Cart to price")
public record CartQuoteRequest(
    @Schema(description = "Country code: UZB, KAZ, GEO, UKR or CHN", example = "UZB")
        @NotBlank(message = "is required")
        String country,
    @Schema(
            description = "PICKUP (default) or DELIVERY",
            example = "DELIVERY",
            allowableValues = {"PICKUP", "DELIVERY"})
        @Pattern(regexp = "PICKUP|DELIVERY", message = "must be PICKUP or DELIVERY")
        String scenario,
    @Schema(
            description = "Delivery zone id. Required for DELIVERY, empty for PICKUP",
            example = "1")
        Long deliveryZoneId,
    @NotEmpty(message = "must not be empty")
        @Size(
            max = PricingLimits.MAX_LINES,
            message = "must have " + PricingLimits.MAX_LINES + " lines or fewer")
        List<@NotNull(message = "must not be null") @Valid CartItemRequest> items) {}
