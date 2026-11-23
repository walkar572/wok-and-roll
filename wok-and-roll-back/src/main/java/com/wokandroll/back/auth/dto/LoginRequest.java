package com.wokandroll.back.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/login. */
public record LoginRequest(
    @Schema(example = "aziz@mail.uz") @NotBlank(message = "is required") String email,
    @Schema(example = "secret123") @NotBlank(message = "is required") String password) {}
