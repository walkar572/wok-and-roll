package com.wokandroll.back.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Response {@code 201} of {@code POST /api/v1/bookings} (api-contract.md section 3.10). */
@Schema(description = "A confirmed booking")
public record BookingResponse(
    @Schema(example = "BK-3F8K2M9P") String bookingNumber,
    @Schema(
            example = "CONFIRMED",
            allowableValues = {"CONFIRMED", "CANCELLED"})
        String status,
    Instant createdAt,
    CafeSummary cafe,
    LocalDate date,
    @Schema(type = "string", example = "19:30") @JsonFormat(pattern = "HH:mm") LocalTime time,
    @Schema(type = "string", example = "21:00") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    @Schema(example = "4") int guests,
    @Schema(example = "4", description = "Seats of the table the server chose") int tableSeats,
    @Schema(example = "Aziz Karimov") String contactName) {

  /** The café of the booking. */
  public record CafeSummary(long id, String name, String address) {}
}
