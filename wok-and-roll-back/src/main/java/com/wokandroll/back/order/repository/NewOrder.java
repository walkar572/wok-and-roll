package com.wokandroll.back.order.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Everything of the {@code customer_order} row that the service decides (not the id and dates). */
public record NewOrder(
    String orderNumber,
    Long userId,
    String countryCode,
    LocalDate orderDate,
    String scenario,
    long cafeId,
    Long deliveryZoneId,
    LocalTime slotStart,
    LocalTime slotEnd,
    String deliveryAddress,
    String contactName,
    String contactPhone,
    String contactEmail,
    String comment,
    String currencyCode,
    BigDecimal subtotal,
    BigDecimal vatRate,
    BigDecimal vat,
    BigDecimal amountInclVat,
    BigDecimal discountRate,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total,
    UUID idempotencyKey,
    String requestHash) {}
