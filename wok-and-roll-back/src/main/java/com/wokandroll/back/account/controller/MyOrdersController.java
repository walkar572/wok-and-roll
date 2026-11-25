package com.wokandroll.back.account.controller;

import com.wokandroll.back.account.dto.OrderSummaryResponse;
import com.wokandroll.back.account.service.MyOrdersService;
import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.PageRequest;
import com.wokandroll.back.common.web.PageResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order history of the logged-in user (api-contract.md section 3.13, #177). */
@RestController
@Tag(name = "Account")
public class MyOrdersController {

  private final MyOrdersService service;

  public MyOrdersController(MyOrdersService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/me/orders")
  @Operation(
      summary = "My orders, newest first",
      description = "Paging with pageSize (1-100, default 20) and the opaque pageToken.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "One page of orders")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_ERROR: pageSize or pageToken",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public PageResponse<OrderSummaryResponse> list(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthUser current,
      @Parameter(example = "20") @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String pageToken) {
    return service.list(current.id(), PageRequest.of(pageSize, pageToken));
  }

  @GetMapping("/api/v1/me/orders/{orderNumber}")
  @Operation(
      summary = "One of my orders with all lines",
      description = "The same body as POST /orders. Another user's order is 404, not 403.",
      security = @SecurityRequirement(name = SecurityConfig.BEARER))
  @ApiResponse(responseCode = "200", description = "The order")
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHORIZED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "NOT_FOUND: unknown, a guest order or not mine",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public OrderResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthUser current,
      @Parameter(example = "WR-7K3M9Q2X") @PathVariable String orderNumber) {
    return service.get(current.id(), orderNumber);
  }
}
