package com.wokandroll.back.pricing.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What pricing needs from the delivery zones: the fee of an active zone of the country. The
 * implementation calls the delivery package (#147, {@code findActive(countryCode, zoneId)}), so
 * pricing does not run a second zone query of its own.
 */
public interface DeliveryZoneFees {

  /** Fee of the active zone, or empty if there is no such active zone in this country. */
  Optional<BigDecimal> activeFee(String countryCode, long zoneId);
}
