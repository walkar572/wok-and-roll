package com.wokandroll.back.pricing.dto;

import java.util.List;

/** One cart line as the pricing service gets it: a product, the chosen modifiers, a quantity. */
public record PricingItem(Long productId, int quantity, List<Long> modifierIds) {}
