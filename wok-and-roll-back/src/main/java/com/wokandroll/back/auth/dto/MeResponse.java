package com.wokandroll.back.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** GET /api/v1/me: the logged-in user. Never contains the password hash. */
public record MeResponse(
    @Schema(example = "15") long id,
    @Schema(example = "aziz@mail.uz") String email,
    @Schema(example = "Aziz Karimov") String fullName,
    @Schema(example = "+998901234567", nullable = true) String phone,
    @Schema(example = "CUSTOMER") String role) {}
