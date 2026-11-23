package com.wokandroll.back.slot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Body of GET /api/v1/time-slots: all slots of one café on one day (api-contract.md 3.8). */
public record TimeSlotsResponse(
    @Schema(example = "1") long cafeId,
    @Schema(description = "Local date of the café", example = "2026-11-20") LocalDate date,
    @Schema(description = "IANA time zone of the café", example = "Asia/Tashkent") String timeZone,
    List<TimeSlotResponse> slots) {}
