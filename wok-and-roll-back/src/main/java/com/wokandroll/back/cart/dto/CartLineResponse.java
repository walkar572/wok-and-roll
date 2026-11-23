package com.wokandroll.back.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/** A priced cart line. Unit price includes the modifiers and is before VAT. */
public record CartLineResponse(
    long productId,
    String name,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    List<CartModifierResponse> modifiers) {}
