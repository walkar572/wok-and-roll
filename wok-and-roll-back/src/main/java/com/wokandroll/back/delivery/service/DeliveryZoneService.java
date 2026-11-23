package com.wokandroll.back.delivery.service;

import com.wokandroll.back.delivery.dto.DeliveryZoneListResponse;
import com.wokandroll.back.delivery.dto.DeliveryZoneResponse;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** Delivery zones for checkout (#147): the guest picks a zone and sees its fee. */
@Service
public class DeliveryZoneService {

  private final DeliveryZoneRepository repository;

  public DeliveryZoneService(DeliveryZoneRepository repository) {
    this.repository = repository;
  }

  public DeliveryZoneListResponse getZones(String countryCode) {
    List<DeliveryZoneResponse> items =
        repository.findAllActive(countryCode).stream()
            .map(DeliveryZoneService::toResponse)
            .toList();
    return new DeliveryZoneListResponse(items);
  }

  private static DeliveryZoneResponse toResponse(DeliveryZoneRow z) {
    return new DeliveryZoneResponse(
        z.id(),
        z.countryCode(),
        z.name(),
        z.cafeId(),
        twoDecimals(z.fee()),
        z.currency(),
        z.deliveryMinutes());
  }

  /** Money always has exactly 2 decimals in the JSON (api-contract.md section 1). */
  private static BigDecimal twoDecimals(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
