package com.wokandroll.back.report.pdf;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The PDF of the reports page (api-contract.md section 3.18, #155), ADMIN and VIEWER. */
@RestController
@Validated
@Tag(name = "Reports")
public class ReportPdfController {

  private final ReportService reports;
  private final SalesPdfRenderer renderer;

  public ReportPdfController(ReportService reports, SalesPdfRenderer renderer) {
    this.reports = reports;
    this.renderer = renderer;
  }

  @GetMapping(value = "/api/v1/reports/sales.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(
      summary = "Both reports as one PDF, with the same numbers as the JSON",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "application/pdf, as an attachment")
  @ApiResponse(
      responseCode = "400",
      description = "The rules of sections 3.16 and 3.17",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "FORBIDDEN: only ADMIN and VIEWER",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<byte[]> salesPdf(
      @Parameter(example = "2026-11-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-30")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(example = "UZB")
          @RequestParam
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(example = "2026") @RequestParam int year) {
    SalesByRegionResponse regions = reports.salesByRegion(from, to);
    SalesByMonthResponse months = reports.salesByMonth(country, year);
    byte[] pdf = renderer.render(regions, months);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(fileName(from, to, country, year))
                .build()
                .toString())
        .body(pdf);
  }

  static String fileName(LocalDate from, LocalDate to, String country, int year) {
    return "wok-and-roll-sales_" + from + "_" + to + "_" + country + "-" + year + ".pdf";
  }
}
