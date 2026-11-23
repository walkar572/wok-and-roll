package com.wokandroll.back.auth;

/** The logged-in user, taken from a valid JWT. Put into the SecurityContext by JwtAuthFilter. */
public record AuthUser(long id, String email, String role) {}
