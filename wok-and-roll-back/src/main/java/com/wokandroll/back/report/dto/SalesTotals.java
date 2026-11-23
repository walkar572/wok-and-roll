package com.wokandroll.back.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** The whole year of one country: the sums of the 12 months. */
public record SalesTotals(
    @Schema(example = "640") long orders,
    @Schema(example = "281120.00") BigDecimal subtotal,
    @Schema(example = "42168.00") BigDecimal vat,
    @Schema(example = "13210.40") BigDecimal discount,
    @Schema(example = "7840.00") BigDecimal deliveryFee,
    @Schema(example = "317917.60") BigDecimal total,
    @Schema(example = "496.75") BigDecimal averageCheck) {}
