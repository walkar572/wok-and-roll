package com.wokandroll.back.cart.service;

import com.wokandroll.back.cart.dto.CartItemRequest;
import com.wokandroll.back.cart.dto.CartQuoteRequest;
import com.wokandroll.back.cart.dto.CartQuoteResponse;
import com.wokandroll.back.pricing.dto.PricingItem;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.service.PricingService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Cart use cases. The numbers always come from the pricing service. */
@Service
public class CartService {

  private final PricingService pricingService;

  public CartService(PricingService pricingService) {
    this.pricingService = pricingService;
  }

  public CartQuoteResponse quote(CartQuoteRequest request) {
    List<PricingItem> items = request.items().stream().map(CartService::toPricingItem).toList();
    return CartQuoteResponse.from(
        pricingService.calculate(
            new PricingRequest(
                request.country(), items, scenarioOf(request), request.deliveryZoneId())));
  }

  /** Pickup is the default. The validation has already checked the text. */
  private static Scenario scenarioOf(CartQuoteRequest request) {
    return request.scenario() == null ? Scenario.PICKUP : Scenario.valueOf(request.scenario());
  }

  private static PricingItem toPricingItem(CartItemRequest item) {
    // The validation has already checked that quantity is a whole number from 1 to int max.
    return new PricingItem(
        item.productId(),
        item.quantity().intValueExact(),
        item.modifierIds() == null ? List.of() : item.modifierIds());
  }
}
