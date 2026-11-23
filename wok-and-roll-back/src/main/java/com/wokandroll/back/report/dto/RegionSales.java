package com.wokandroll.back.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One country in the sales-by-region report. Money is in the country's own currency. */
public record RegionSales(
    @Schema(example = "UZB") String country,
    @Schema(example = "Uzbekistan") String countryName,
    @Schema(example = "UZS") String currency,
    @Schema(example = "120") long orders,
    @Schema(example = "52340.00") BigDecimal subtotal,
    @Schema(example = "7851.00") BigDecimal vat,
    @Schema(example = "2410.35") BigDecimal discount,
    @Schema(example = "1460.00") BigDecimal deliveryFee,
    @Schema(example = "59240.65") BigDecimal total,
    @Schema(example = "493.67", description = "total / orders, HALF_UP; 0.00 without orders")
        BigDecimal averageCheck) {}
