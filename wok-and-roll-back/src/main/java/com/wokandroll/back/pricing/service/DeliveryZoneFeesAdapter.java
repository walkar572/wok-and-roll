package com.wokandroll.back.pricing.service;

import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Gives pricing the fee of a delivery zone from the delivery package (#147). Pricing has no zone
 * query of its own: the fee in the quote is the fee in the zone list (api-contract.md 3.7).
 */
@Component
class DeliveryZoneFeesAdapter implements DeliveryZoneFees {

  private final DeliveryZoneRepository zones;

  DeliveryZoneFeesAdapter(DeliveryZoneRepository zones) {
    this.zones = zones;
  }

  @Override
  public Optional<BigDecimal> activeFee(String countryCode, long zoneId) {
    return zones.findActive(countryCode, zoneId).map(DeliveryZoneRow::fee);
  }
}
