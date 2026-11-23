package com.wokandroll.back.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Answer of register and login. The token goes into "Authorization: Bearer <token>". */
public record AuthResponse(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String token,
    @Schema(
            example = "CUSTOMER",
            allowableValues = {"ADMIN", "STAFF", "VIEWER", "CUSTOMER"})
        String role) {}
