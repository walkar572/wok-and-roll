package com.wokandroll.back.order.service;

import com.wokandroll.back.order.repository.CafeRow;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import java.time.LocalDate;
import java.time.LocalTime;

/** An order request that passed every check: cleaned values, the café, and the price. */
public record ValidOrder(
    String countryCode,
    LocalDate orderDate,
    String scenario,
    CafeRow cafe,
    Long deliveryZoneId,
    LocalTime slotStart,
    LocalTime slotEnd,
    String deliveryAddress,
    String contactName,
    String contactPhone,
    String contactEmail,
    String comment,
    PriceBreakdown price) {}
