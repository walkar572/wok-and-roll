package com.wokandroll.back.pricing.dto;

import java.util.List;

/**
 * Input of the pricing service. There are no prices here: the service reads them itself. The zone
 * is needed only for DELIVERY.
 */
public record PricingRequest(
    String countryCode, List<PricingItem> items, Scenario scenario, Long deliveryZoneId) {

  /** A pickup request, the default of the quote. */
  public PricingRequest(String countryCode, List<PricingItem> items) {
    this(countryCode, items, Scenario.PICKUP, null);
  }
}
