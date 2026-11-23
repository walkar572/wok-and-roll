package com.wokandroll.back.report.csv;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import com.wokandroll.back.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CSV of each sales report (api-contract.md section 3.19, #172), ADMIN and VIEWER. */
@RestController
@Validated
@Tag(name = "Reports")
public class ReportCsvController {

  static final String TEXT_CSV = "text/csv";
  private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

  private final ReportService reports;
  private final SalesCsvWriter writer;

  public ReportCsvController(ReportService reports, SalesCsvWriter writer) {
    this.reports = reports;
    this.writer = writer;
  }

  @GetMapping(value = "/api/v1/reports/sales-by-region.csv", produces = TEXT_CSV)
  @Operation(
      summary = "Sales by region as CSV, the same rows and numbers as the JSON",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "text/csv, as an attachment")
  @ApiResponse(
      responseCode = "400",
      description = "The rules of section 3.16",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: only ADMIN and VIEWER",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<byte[]> salesByRegion(
      @Parameter(example = "2026-11-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-30")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    String csv = writer.salesByRegion(reports.salesByRegion(from, to));
    return attachment(csv, regionFileName(from, to));
  }

  @GetMapping(value = "/api/v1/reports/sales-by-month.csv", produces = TEXT_CSV)
  @Operation(
      summary = "Sales by month as CSV, the same rows and numbers as the JSON",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "text/csv, as an attachment")
  @ApiResponse(
      responseCode = "400",
      description = "The rules of section 3.17",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: only ADMIN and VIEWER",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<byte[]> salesByMonth(
      @Parameter(example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(example = "2026") @RequestParam int year) {
    String csv = writer.salesByMonth(reports.salesByMonth(country, year));
    return attachment(csv, monthFileName(country, year));
  }

  private static ResponseEntity<byte[]> attachment(String csv, String fileName) {
    return ResponseEntity.ok()
        .contentType(CSV_UTF8)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }

  static String regionFileName(LocalDate from, LocalDate to) {
    return "wok-and-roll-sales-by-region_" + from + "_" + to + ".csv";
  }

  static String monthFileName(String country, int year) {
    return "wok-and-roll-sales-by-month_" + country + "_" + year + ".csv";
  }
}
