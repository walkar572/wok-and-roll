package com.wokandroll.back.staff;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import com.wokandroll.back.staff.StaffDtos.StaffBookingResponse;
import com.wokandroll.back.staff.StaffDtos.StaffOrderResponse;
import com.wokandroll.back.staff.StaffDtos.StatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Café work list, STAFF and ADMIN (api-contract.md section 3.14, #176). */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff")
@ApiResponse(
    responseCode = "401",
    description = "UNAUTHORIZED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
    responseCode = "403",
    description = "FORBIDDEN: only STAFF and ADMIN",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class StaffController {

  private final StaffService service;

  public StaffController(StaffService service) {
    this.service = service;
  }

  @GetMapping("/orders")
  @Operation(
      summary = "Orders of a café for a date, by slot",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public PageResponse<StaffOrderResponse> orders(
      @Parameter(example = "1") @RequestParam long cafeId,
      @Parameter(example = "2026-11-20")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.orders(cafeId, date, PageRequest.of(pageSize, pageToken));
  }

  @PatchMapping("/orders/{orderNumber}")
  @Operation(
      summary = "Change the status of an order",
      description = "NEW → CONFIRMED → DONE; NEW or CONFIRMED → CANCELLED (frees the slot place).",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(
      responseCode = "409",
      description = "INVALID_STATUS_CHANGE",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public OrderResponse changeStatus(
      @PathVariable String orderNumber, @RequestBody StatusRequest request) {
    return service.changeStatus(orderNumber, request.status());
  }

  @GetMapping("/bookings")
  @Operation(
      summary = "Bookings of a café for a date, by time",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  public Map<String, List<StaffBookingResponse>> bookings(
      @Parameter(example = "1") @RequestParam long cafeId,
      @Parameter(example = "2026-11-21")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return Map.of("items", service.bookings(cafeId, date));
  }
}
