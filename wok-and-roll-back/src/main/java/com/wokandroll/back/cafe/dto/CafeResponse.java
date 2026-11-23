package com.wokandroll.back.cafe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One café: pickup point, booking venue and kitchen for its delivery zones (contract 3.6). */
public record CafeResponse(
    @Schema(example = "1") long id,
    @Schema(example = "UZB") String country,
    @Schema(example = "Wok & Roll Chilanzar") String name,
    @Schema(example = "12 Bunyodkor Ave, Tashkent") String address,
    @Schema(example = "+998712000001", nullable = true) String phone,
    @Schema(description = "Local time, HH:mm", example = "10:00") String opensAt,
    @Schema(description = "Local time, HH:mm", example = "22:00") String closesAt,
    @Schema(description = "IANA time zone", example = "Asia/Tashkent") String timeZone) {}
