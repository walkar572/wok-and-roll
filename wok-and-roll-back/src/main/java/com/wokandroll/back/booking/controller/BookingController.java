package com.wokandroll.back.booking.controller;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.booking.dto.BookingRequest;
import com.wokandroll.back.booking.dto.BookingResponse;
import com.wokandroll.back.booking.service.BookingService;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.order.service.IdempotencyKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/v1/bookings: book a table (api-contract.md section 3.10, #174). */
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings")
public class BookingController {

  static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Book a table",
      description =
          "The server takes the smallest free table with enough seats for 90 minutes. Guests can"
              + " book; with a valid token the booking is linked to the user. Idempotency-Key"
              + " (UUID, optional) makes a retry safe.")
  @ApiResponse(responseCode = "201", description = "Booking confirmed (or replayed)")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR, UNKNOWN_CAFE, PAST_DATE. All problems are in fields; code is the"
              + " code of the first one.",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "The token is invalid or expired",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "NO_TABLE_AVAILABLE: no free table for this time and number of guests",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "IDEMPOTENCY_KEY_REUSED",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<BookingResponse> book(
      @RequestBody BookingRequest request,
      @Parameter(description = "UUID. Makes a retry safe.")
          @RequestHeader(value = IdempotencyKeys.HEADER, required = false)
          String idempotencyKey,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthUser current) {
    UUID key = IdempotencyKeys.parse(idempotencyKey);
    BookingService.Result result =
        service.book(request, key, current == null ? null : current.id());
    ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
    if (result.replayed()) {
      response.header(REPLAYED_HEADER, "true");
    }
    return response.body(result.booking());
  }
}
