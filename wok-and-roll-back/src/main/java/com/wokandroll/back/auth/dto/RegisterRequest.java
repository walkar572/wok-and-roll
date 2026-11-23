package com.wokandroll.back.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/register. Register always gives the role CUSTOMER. */
public record RegisterRequest(
    @Schema(example = "aziz@mail.uz")
        @NotBlank(message = "is required")
        @Email(message = "must be a valid email")
        @Size(max = 255, message = "must be 255 characters or less")
        String email,
    @Schema(example = "secret123")
        @NotBlank(message = "is required")
        @Size(min = 8, max = 72, message = "must be 8 to 72 characters")
        String password,
    @Schema(example = "Aziz Karimov")
        @NotBlank(message = "is required")
        @Size(max = 150, message = "must be 150 characters or less")
        String fullName,
    @Schema(example = "+998901234567", nullable = true)
        @Size(max = 30, message = "must be 30 characters or less")
        String phone) {}
