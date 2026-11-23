package com.wokandroll.back.order.controller;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.order.dto.OrderRequest;
import com.wokandroll.back.order.dto.OrderResponse;
import com.wokandroll.back.order.service.IdempotencyKeys;
import com.wokandroll.back.order.service.OrderResult;
import com.wokandroll.back.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

  static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  @Operation(
      summary = "Place an order",
      description =
          "Prices the cart again on the server, takes a place in the time slot and saves the"
              + " order. Guests can order; with a valid token the order is linked to the user."
              + " Idempotency-Key (UUID, optional): the same key and body again returns the first"
              + " order with the header Idempotent-Replayed: true.")
  @ApiResponse(responseCode = "201", description = "Order placed (or replayed)")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR, UNKNOWN_COUNTRY, UNKNOWN_CAFE, UNKNOWN_DELIVERY_ZONE, PAST_DATE,"
              + " INVALID_SLOT and the cart codes of the quote. All problems are in fields; code is"
              + " the code of the first one.",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "The Authorization header is given but the token is invalid or expired",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "SLOT_FULL or PRICE_CHANGED (nothing is saved)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "IDEMPOTENCY_KEY_REUSED: the key was used with a different request",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<OrderResponse> place(
      @RequestBody OrderRequest request,
      @Parameter(description = "UUID. Makes a retry safe.")
          @RequestHeader(value = IdempotencyKeys.HEADER, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false)
          String authorization,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthUser current) {
    // A bad token is never treated as a guest: the order must not silently lose its owner.
    if (authorization != null && current == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or expired token");
    }
    UUID key = IdempotencyKeys.parse(idempotencyKey);
    OrderResult result = orderService.place(request, key, current == null ? null : current.id());

    ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
    if (current != null) {
      response.location(URI.create("/api/v1/me/orders/" + result.order().orderNumber()));
    }
    if (result.replayed()) {
      response.header(REPLAYED_HEADER, "true");
    }
    return response.body(result.order());
  }
}
