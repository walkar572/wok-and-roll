package com.wokandroll.back.pricing.repository;

import java.math.BigDecimal;

/** A country with its currency and VAT rate (percent). {@code vatRate} is null if not set. */
public record CountryPricing(long id, String code, String currencyCode, BigDecimal vatRate) {}
