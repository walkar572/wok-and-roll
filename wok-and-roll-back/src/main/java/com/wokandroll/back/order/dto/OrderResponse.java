package com.wokandroll.back.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wokandroll.back.cart.dto.CartLineResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Response {@code 201} of {@code POST /api/v1/orders} (api-contract.md §3.9). */
@Schema(description = "A placed order")
public record OrderResponse(
    @Schema(example = "WR-7K3M9Q2X") String orderNumber,
    @Schema(example = "NEW") String status,
    Instant createdAt,
    @Schema(example = "UZB") String country,
    @Schema(example = "UZS") String currency,
    LocalDate orderDate,
    @Schema(type = "string", example = "12:30") @JsonFormat(pattern = "HH:mm") LocalTime slotStart,
    @Schema(type = "string", example = "13:00") @JsonFormat(pattern = "HH:mm") LocalTime slotEnd,
    @Schema(
            example = "DELIVERY",
            allowableValues = {"PICKUP", "DELIVERY"})
        String scenario,
    CafeSummary cafe,
    @Schema(description = "null for PICKUP") ZoneSummary deliveryZone,
    @Schema(description = "null for PICKUP") String deliveryAddress,
    String contactName,
    String contactPhone,
    List<CartLineResponse> lines,
    @Schema(example = "170.00") BigDecimal subtotal,
    @Schema(description = "VAT rate in percent", example = "15.00") BigDecimal vatRate,
    @Schema(example = "25.50") BigDecimal vat,
    @Schema(example = "195.50") BigDecimal amountInclVat,
    @Schema(description = "Discount rate in percent", example = "3.00") BigDecimal discountRate,
    @Schema(example = "5.87") BigDecimal discount,
    @Schema(description = "0.00 for PICKUP", example = "20.00") BigDecimal deliveryFee,
    @Schema(example = "209.63") BigDecimal total) {

  /** The café that prepares the order. */
  public record CafeSummary(Long id, String name, String address) {}

  /** The delivery zone of a DELIVERY order. */
  public record ZoneSummary(Long id, String name) {}
}
