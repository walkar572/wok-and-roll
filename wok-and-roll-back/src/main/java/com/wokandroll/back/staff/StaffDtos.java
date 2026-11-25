package com.wokandroll.back.staff;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;

/** Bodies of the staff API (api-contract.md section 3.14, #176). */
public final class StaffDtos {

  private StaffDtos() {}

  /** One order in the café's work list. */
  public record StaffOrderResponse(
      @Schema(example = "WR-7K3M9Q2X") String orderNumber,
      @Schema(type = "string", example = "12:30") @JsonFormat(pattern = "HH:mm")
          LocalTime slotStart,
      @Schema(type = "string", example = "13:00") @JsonFormat(pattern = "HH:mm") LocalTime slotEnd,
      @Schema(example = "DELIVERY") String scenario,
      @Schema(example = "NEW") String status,
      @Schema(example = "Aziz Karimov") String contactName,
      @Schema(example = "+998901234567") String contactPhone,
      @Schema(nullable = true, example = "Amir Temur 15, apt 4") String deliveryAddress,
      @Schema(example = "3") int itemCount,
      @Schema(example = "209.63") BigDecimal total,
      @Schema(example = "UZS") String currency) {}

  /** PATCH /staff/orders/{orderNumber}. */
  public record StatusRequest(
      @Schema(
              example = "CONFIRMED",
              allowableValues = {"CONFIRMED", "DONE", "CANCELLED"})
          String status) {}

  /** One booking in the café's list. */
  public record StaffBookingResponse(
      @Schema(example = "BK-3F8K2M9P") String bookingNumber,
      @Schema(type = "string", example = "19:30") @JsonFormat(pattern = "HH:mm") LocalTime time,
      @Schema(type = "string", example = "21:00") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
      @Schema(example = "4") int guests,
      @Schema(example = "4") int tableSeats,
      @Schema(example = "CONFIRMED") String status,
      @Schema(example = "Aziz Karimov") String contactName,
      @Schema(example = "+998901234567") String contactPhone) {}
}
