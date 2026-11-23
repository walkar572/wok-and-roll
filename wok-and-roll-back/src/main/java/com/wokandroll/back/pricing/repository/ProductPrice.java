package com.wokandroll.back.pricing.repository;

import java.math.BigDecimal;

/**
 * A product as seen in one country. {@code visible} is false if the product or its category is
 * hidden. {@code price} is null if the product has no regional price (not sold in this country).
 */
public record ProductPrice(
    long id, String name, boolean visible, BigDecimal price, boolean inStock) {}
