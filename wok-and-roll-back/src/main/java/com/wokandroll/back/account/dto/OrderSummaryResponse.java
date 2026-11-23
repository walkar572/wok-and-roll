package com.wokandroll.back.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** One order in "My orders" (api-contract.md section 3.13). */
public record OrderSummaryResponse(
    @Schema(example = "WR-7K3M9Q2X") String orderNumber,
    Instant createdAt,
    LocalDate orderDate,
    @Schema(type = "string", example = "12:30") @JsonFormat(pattern = "HH:mm") LocalTime slotStart,
    @Schema(example = "UZB") String country,
    @Schema(example = "UZS") String currency,
    @Schema(
            example = "DELIVERY",
            allowableValues = {"PICKUP", "DELIVERY"})
        String scenario,
    @Schema(
            example = "NEW",
            allowableValues = {"NEW", "CONFIRMED", "DONE", "CANCELLED"})
        String status,
    @Schema(example = "3", description = "Sum of the quantities of all lines") int itemCount,
    @Schema(example = "209.63") BigDecimal total) {}
