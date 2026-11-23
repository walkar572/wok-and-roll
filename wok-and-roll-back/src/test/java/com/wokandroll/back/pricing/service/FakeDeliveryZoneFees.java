package com.wokandroll.back.pricing.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory delivery zones for tests: the key is the country code and the zone id. */
public class FakeDeliveryZoneFees implements DeliveryZoneFees {

  private final Map<String, BigDecimal> fees = new HashMap<>();

  /** UZB zone 1 with fee 20.00, UZB zone 2 with fee 15.00, KAZ zone 5 with fee 20.00. */
  public static FakeDeliveryZoneFees sample() {
    return new FakeDeliveryZoneFees()
        .with("UZB", 1, "20.00")
        .with("UZB", 2, "15.00")
        .with("KAZ", 5, "20.00");
  }

  public FakeDeliveryZoneFees with(String countryCode, long zoneId, String fee) {
    fees.put(countryCode + "/" + zoneId, new BigDecimal(fee));
    return this;
  }

  @Override
  public Optional<BigDecimal> activeFee(String countryCode, long zoneId) {
    return Optional.ofNullable(fees.get(countryCode + "/" + zoneId));
  }
}
