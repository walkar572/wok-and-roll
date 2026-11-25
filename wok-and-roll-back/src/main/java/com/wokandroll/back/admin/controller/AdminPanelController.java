package com.wokandroll.back.admin.controller;

import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminBookingSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminCafeListResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminOrderResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminOrderSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminUserSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.OverviewResponse;
import com.wokandroll.back.admin.repository.AdminPanelRepository.ListFilter;
import com.wokandroll.back.admin.service.AdminPanelService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.CountryCodes;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin panel API, ADMIN only and read only (api-contract.md section 3.20, v1.11): the numbers
 * of a period, every order, booking, account and café. The lists use paging (section 1.1).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Validated
@Tag(name = "Admin panel")
@ApiResponse(
    responseCode = "400",
    description =
        "BAD_REQUEST: a date that is not a date. VALIDATION_ERROR: a wrong filter or page",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
    responseCode = "401",
    description = "UNAUTHORIZED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
    responseCode = "403",
    description = "FORBIDDEN: only ADMIN",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class AdminPanelController {

  static final String ORDER_STATUS = "NEW|CONFIRMED|DONE|CANCELLED";
  static final String BOOKING_STATUS = "CONFIRMED|CANCELLED";
  static final String ROLE = "ADMIN|STAFF|VIEWER|CUSTOMER";
  static final int MAX_QUERY = 100;

  private final AdminPanelService service;

  public AdminPanelController(AdminPanelService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  @Operation(
      summary = "The numbers of a period: orders, bookings, accounts, revenue, days, top dishes",
      description = "Money per country in its own currency, as in the sales-by-region report.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public OverviewResponse overview(
      @Parameter(example = "2026-10-26")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-24")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return service.overview(from, to);
  }

  @GetMapping("/orders")
  @Operation(
      summary = "Every order, newest day first, with filters",
      description =
          "q looks in the order number, contact name, phone and email, and the account email.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public PageResponse<AdminOrderSummary> orders(
      @Parameter(example = "UZB")
          @RequestParam(required = false)
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(example = "1") @RequestParam(required = false) Long cafeId,
      @Parameter(example = "NEW")
          @RequestParam(required = false)
          @Pattern(
              regexp = ORDER_STATUS,
              message = "must be one of NEW, CONFIRMED, DONE, CANCELLED")
          String status,
      @Parameter(example = "2026-11-01")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-30")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(example = "Karimov")
          @RequestParam(required = false)
          @Size(max = MAX_QUERY, message = "must be at most 100 characters")
          String q,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.orders(
        new ListFilter(country, cafeId, status, from, to, AdminPanelService.query(q)),
        PageRequest.of(pageSize, pageToken));
  }

  @GetMapping("/orders/{orderNumber}")
  @Operation(
      summary = "One order: the body of POST /orders, plus email, comment, account, last change",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public AdminOrderResponse order(@PathVariable String orderNumber) {
    return service.order(orderNumber);
  }

  @GetMapping("/bookings")
  @Operation(
      summary = "Every booking, newest day first, with filters",
      description = "q looks in the booking number, contact name, phone and email.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public PageResponse<AdminBookingSummary> bookings(
      @Parameter(example = "UZB")
          @RequestParam(required = false)
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Parameter(example = "1") @RequestParam(required = false) Long cafeId,
      @Parameter(example = "CONFIRMED")
          @RequestParam(required = false)
          @Pattern(regexp = BOOKING_STATUS, message = "must be one of CONFIRMED, CANCELLED")
          String status,
      @Parameter(example = "2026-11-01")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2026-11-30")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @Parameter(example = "Beridze")
          @RequestParam(required = false)
          @Size(max = MAX_QUERY, message = "must be at most 100 characters")
          String q,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.bookings(
        new ListFilter(country, cafeId, status, from, to, AdminPanelService.query(q)),
        PageRequest.of(pageSize, pageToken));
  }

  @GetMapping("/users")
  @Operation(
      summary = "Every account, newest first, with its number of orders; never a password hash",
      description = "q looks in the email, name and phone.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public PageResponse<AdminUserSummary> users(
      @Parameter(example = "CUSTOMER")
          @RequestParam(required = false)
          @Pattern(regexp = ROLE, message = "must be one of ADMIN, STAFF, VIEWER, CUSTOMER")
          String role,
      @Parameter(example = "demo")
          @RequestParam(required = false)
          @Size(max = MAX_QUERY, message = "must be at most 100 characters")
          String q,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.users(role, AdminPanelService.query(q), PageRequest.of(pageSize, pageToken));
  }

  @GetMapping("/cafes")
  @Operation(
      summary = "Every café, closed ones too, with its tables and today's orders and bookings",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public AdminCafeListResponse cafes() {
    return service.cafes();
  }
}
