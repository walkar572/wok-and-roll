package com.wokandroll.back.delivery.dto;

import java.util.List;

/** Body of GET /api/v1/delivery-zones. An object, so fields can be added later (contract 1). */
public record DeliveryZoneListResponse(List<DeliveryZoneResponse> items) {}
