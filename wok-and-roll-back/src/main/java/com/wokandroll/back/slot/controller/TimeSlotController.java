package com.wokandroll.back.slot.controller;

import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.slot.dto.TimeSlotsResponse;
import com.wokandroll.back.slot.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/time-slots: slots of a café with free places (#148, api-contract.md 3.8). */
@RestController
@RequestMapping("/api/v1/time-slots")
@Validated
@Tag(name = "Time slots")
public class TimeSlotController {

  private final SlotService service;

  public TimeSlotController(SlotService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Slots of a café on one day with free places",
      description =
          "30-minute slots in the café's local time. available is false when the slot is full"
              + " (FULL) or starts in less than 30 minutes (PAST). The list is information only:"
              + " the real check is in POST /orders.")
  @ApiResponse(responseCode = "200", description = "All slots of the day")
  @ApiResponse(
      responseCode = "400",
      description = "Unknown café, date not from today to 7 days ahead, or a parameter missing",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public TimeSlotsResponse getSlots(
      @Parameter(description = "Café id", example = "1")
          @RequestParam
          @Positive(message = "must be a positive number")
          long cafeId,
      @Parameter(description = "Date YYYY-MM-DD in the café's time zone", example = "2026-11-20")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return service.getSlots(cafeId, date);
  }
}
