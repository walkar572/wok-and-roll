package com.wokandroll.back.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

/** One priced cart line. Unit price = regional price + modifier deltas, before VAT. */
public record PricedLine(
    long productId,
    String name,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    List<PricedModifier> modifiers) {}
