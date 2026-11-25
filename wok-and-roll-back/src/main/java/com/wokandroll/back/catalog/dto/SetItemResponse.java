package com.wokandroll.back.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One dish of a set and how many of it the set has (api-contract.md 3.3, v1.10). */
public record SetItemResponse(
    @Schema(example = "11") long productId,
    @Schema(example = "Philadelphia roll") String name,
    @Schema(example = "2") int quantity) {}
