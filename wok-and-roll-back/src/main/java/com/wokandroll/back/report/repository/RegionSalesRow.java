package com.wokandroll.back.report.repository;

import java.math.BigDecimal;

/** Sums of the saved order values of one country, straight from SQL. */
public record RegionSalesRow(
    String country,
    String countryName,
    String currency,
    long orders,
    BigDecimal subtotal,
    BigDecimal vat,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total) {}
