package com.wokandroll.back.cafe.dto;

import java.util.List;

/** Body of GET /api/v1/cafes. An object, so fields can be added later (contract 1). */
public record CafeListResponse(List<CafeResponse> items) {}
