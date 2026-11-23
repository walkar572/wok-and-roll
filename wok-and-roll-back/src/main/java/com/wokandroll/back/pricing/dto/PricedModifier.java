package com.wokandroll.back.pricing.dto;

import java.math.BigDecimal;

/** A modifier chosen for a line, with its type and its price change in the local currency. */
public record PricedModifier(long id, String name, ModifierType type, BigDecimal priceDelta) {}
