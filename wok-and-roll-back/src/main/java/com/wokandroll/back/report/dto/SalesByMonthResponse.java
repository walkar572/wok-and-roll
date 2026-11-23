package com.wokandroll.back.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** GET /api/v1/reports/sales-by-month (api-contract.md section 3.17). Always 12 months. */
public record SalesByMonthResponse(
    @Schema(example = "UZB") String country,
    @Schema(example = "Uzbekistan") String countryName,
    @Schema(example = "UZS") String currency,
    @Schema(example = "2026") int year,
    @Schema(example = "2026-11-20T09:15:00Z") Instant generatedAt,
    List<MonthSales> months,
    SalesTotals totals) {}
