package com.wokandroll.back.report.repository;

import java.math.BigDecimal;

/** Sums of the saved order values of one month, straight from SQL. */
public record MonthSalesRow(
    int month,
    long orders,
    BigDecimal subtotal,
    BigDecimal vat,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total) {}
