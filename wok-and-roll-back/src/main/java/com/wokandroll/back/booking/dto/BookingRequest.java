package com.wokandroll.back.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request of {@code POST /api/v1/bookings} (api-contract.md section 3.10). The rules are checked by
 * BookingValidator, so all problems come back together. Date and time are text on purpose: a wrong
 * format is a field problem, not an unreadable body.
 */
@Schema(description = "Table to book")
public record BookingRequest(
    @Schema(example = "1") Long cafeId,
    @Schema(type = "string", example = "2026-11-21", description = "YYYY-MM-DD") String date,
    @Schema(type = "string", example = "19:30", description = "HH:mm, on a 30-minute step")
        String time,
    @Schema(example = "4", description = "1 to 8") Integer guests,
    @Schema(example = "Aziz Karimov") String contactName,
    @Schema(example = "+998901234567") String contactPhone,
    @Schema(example = "aziz@mail.uz") String contactEmail,
    @Schema(example = "Window seat if possible") String comment) {}
