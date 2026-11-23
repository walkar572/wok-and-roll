package com.wokandroll.back.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One delivery zone with its fixed fee (api-contract.md 3.7). */
public record DeliveryZoneResponse(
    @Schema(example = "1") long id,
    @Schema(example = "UZB") String country,
    @Schema(example = "Tashkent: Center") String name,
    @Schema(description = "The café that cooks for this zone", example = "1") long cafeId,
    @Schema(description = "Local currency, outside VAT", example = "20.00") BigDecimal fee,
    @Schema(description = "ISO 4217 currency code", example = "UZS") String currency,
    @Schema(description = "Delivery time in minutes", example = "45") int deliveryMinutes) {}
