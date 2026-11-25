package com.wokandroll.back.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/reports/sales-by-region (api-contract.md section 3.16). Always all countries. There
 * is no money sum across countries, because every country has its own currency.
 */
public record SalesByRegionResponse(
    @Schema(example = "2026-11-01") LocalDate from,
    @Schema(example = "2026-11-30") LocalDate to,
    @Schema(example = "2026-11-20T09:15:00Z") Instant generatedAt,
    @Schema(example = "412", description = "Orders of all countries together") long totalOrders,
    List<RegionSales> regions) {}
