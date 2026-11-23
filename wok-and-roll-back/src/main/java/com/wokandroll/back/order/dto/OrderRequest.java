package com.wokandroll.back.order.dto;

import com.wokandroll.back.cart.dto.CartItemRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request of {@code POST /api/v1/orders} (api-contract.md §3.9). There are no prices: the backend
 * prices the cart again. The field rules (required, lengths, formats) are checked by {@code
 * OrderValidator} (#150), so the fields are not annotated here. The date and the time are text on
 * purpose: a wrong format must be a field problem, not an unreadable body.
 */
@Schema(description = "Order to place")
public record OrderRequest(
    @Schema(example = "UZB") String country,
    @Schema(type = "string", example = "2026-11-20", description = "YYYY-MM-DD") String orderDate,
    @Schema(
            example = "DELIVERY",
            allowableValues = {"PICKUP", "DELIVERY"})
        String scenario,
    @Schema(description = "PICKUP only: a café of the country") Long cafeId,
    @Schema(description = "DELIVERY only: a zone of the country") Long deliveryZoneId,
    @Schema(description = "DELIVERY only, 5 to 200 characters", example = "Amir Temur 15, apt 4")
        String deliveryAddress,
    @Schema(type = "string", example = "12:30", description = "HH:mm, a slot of the café")
        String slotStart,
    @Schema(example = "Aziz Karimov") String contactName,
    @Schema(example = "+998901234567") String contactPhone,
    @Schema(example = "aziz@mail.uz") String contactEmail,
    @Schema(example = "Call at the door") String comment,
    @Schema(description = "Total of the last quote the guest saw", example = "209.63")
        BigDecimal expectedTotal,
    List<CartItemRequest> items) {}
