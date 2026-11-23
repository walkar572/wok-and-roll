package com.wokandroll.back.report.controller;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sales reports for ADMIN and VIEWER (api-contract.md section 3.16, #153). */
@RestController
@Validated
@Tag(name = "Reports")
public class ReportController {

  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/reports/sales-by-region")
  @Operation(
      summary = "Sales by region for a period, each country in its own currency",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "All 5 countries, zeros without orders")
  @ApiResponse(
      responseCode = "400",
      description =
          "BAD_REQUEST: from or to missing or not a date. VALIDATION_ERROR: from after"
              + " to, or more than 366 days",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: only ADMIN and VIEWER",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public SalesByRegionResponse salesByRegion(
      @Parameter(example = "2026-11-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-30")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return service.salesByRegion(from, to);
  }

  @GetMapping("/api/v1/reports/sales-by-month")
  @Operation(
      summary = "Sales of one country by month for a year, in its own currency",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "Always 12 months, zeros without orders")
  @ApiResponse(
      responseCode = "400",
      description =
          "BAD_REQUEST: country or year missing or not a number. VALIDATION_ERROR: unknown"
              + " country, or year outside 2024 to next year",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: only ADMIN and VIEWER",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public SalesByMonthResponse salesByMonth(
      @Parameter(example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(example = "2026") @RequestParam int year) {
    return service.salesByMonth(country, year);
  }
}
