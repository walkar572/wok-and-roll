package com.wokandroll.back.slot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One 30-minute slot of a café with its free places (api-contract.md 3.8). */
public record TimeSlotResponse(
    @Schema(description = "Local time, HH:mm", example = "10:00") String start,
    @Schema(description = "Local time, HH:mm", example = "10:30") String end,
    @Schema(description = "Orders per slot of this café", example = "5") int capacity,
    @Schema(description = "Free places, 0 or more", example = "5") int remaining,
    @Schema(description = "False when FULL or PAST", example = "true") boolean available,
    @Schema(description = "FULL, PAST or null", nullable = true) String reason) {}
