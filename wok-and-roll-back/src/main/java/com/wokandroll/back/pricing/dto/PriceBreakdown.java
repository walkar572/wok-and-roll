package com.wokandroll.back.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the pricing service. All money has 2 decimals, rates are percents. Total = amount incl.
 * VAT - discount + delivery fee.
 */
public record PriceBreakdown(
    String country,
    String currency,
    Scenario scenario,
    List<PricedLine> lines,
    BigDecimal subtotal,
    BigDecimal vatRate,
    BigDecimal vat,
    BigDecimal amountInclVat,
    BigDecimal discountRate,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total) {}
