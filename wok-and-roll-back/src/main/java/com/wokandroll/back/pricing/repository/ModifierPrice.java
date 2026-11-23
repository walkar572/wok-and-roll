package com.wokandroll.back.pricing.repository;

import com.wokandroll.back.pricing.dto.ModifierType;
import java.math.BigDecimal;

/**
 * A modifier as seen in one country. {@code priceDelta} is null if the modifier has no price in
 * this country.
 */
public record ModifierPrice(
    long id,
    long productId,
    ModifierType type,
    String name,
    boolean visible,
    BigDecimal priceDelta) {}
