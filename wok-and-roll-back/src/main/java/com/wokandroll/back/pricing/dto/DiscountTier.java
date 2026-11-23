package com.wokandroll.back.pricing.dto;

import java.math.BigDecimal;

/**
 * One row of {@code discount_threshold} for a country: from {@code minAmount} (Amount incl. VAT, in
 * the local currency) the discount is {@code ratePercent} percent, for example 100.00 and 3.00.
 */
public record DiscountTier(BigDecimal minAmount, BigDecimal ratePercent) {}
