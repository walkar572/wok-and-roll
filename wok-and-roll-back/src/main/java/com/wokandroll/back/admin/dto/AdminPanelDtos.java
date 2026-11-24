package com.wokandroll.back.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wokandroll.back.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Bodies of the admin panel API, ADMIN only (api-contract.md section 3.20, v1.11). */
public final class AdminPanelDtos {

  private AdminPanelDtos() {}

  /** A café or a zone in a list row: the id and the English name. */
  public record Named(
      @Schema(example = "1") long id, @Schema(example = "Wok & Roll Chilanzar") String name) {}

  /** The account that placed an order or a booking; null for a guest. */
  public record Account(
      @Schema(example = "customer@wokandroll.test") String email,
      @Schema(example = "Demo Customer") String fullName) {}

  // ---- overview ------------------------------------------------------------------------------

  /** GET /admin/overview: the numbers of a period for the first page of the panel. */
  public record OverviewResponse(
      @Schema(example = "2026-10-26") LocalDate from,
      @Schema(example = "2026-11-24") LocalDate to,
      @Schema(example = "2026-11-24T09:15:00Z") Instant generatedAt,
      OrderCounts orders,
      BookingCounts bookings,
      AccountCounts accounts,
      @Schema(description = "Every country, in its own currency, without cancelled orders")
          List<RegionRevenue> regions,
      @Schema(description = "Every day of the period, all countries, without cancelled orders")
          List<DayOrders> days,
      @Schema(description = "The 5 dishes ordered most, by quantity, without cancelled orders")
          List<TopDish> topDishes) {}

  public record OrderCounts(
      @Schema(example = "1210", description = "All orders of the period, cancelled too") long total,
      @Schema(example = "{\"NEW\": 5, \"CONFIRMED\": 3, \"DONE\": 1190, \"CANCELLED\": 12}")
          Map<String, Long> byStatus,
      @Schema(example = "800") long pickup,
      @Schema(example = "410") long delivery) {}

  public record BookingCounts(
      @Schema(example = "420", description = "All bookings of the period, cancelled too")
          long total,
      @Schema(example = "21") long cancelled,
      @Schema(example = "1130", description = "Guests of the confirmed bookings") long guests) {}

  public record AccountCounts(
      @Schema(example = "4") long total,
      @Schema(example = "1", description = "Accounts created in the period (UTC dates)")
          long newInPeriod,
      @Schema(example = "{\"ADMIN\": 1, \"STAFF\": 1, \"VIEWER\": 1, \"CUSTOMER\": 1}")
          Map<String, Long> byRole) {}

  public record RegionRevenue(
      @Schema(example = "UZB") String country,
      @Schema(example = "UZS") String currency,
      @Schema(example = "300") long orders,
      @Schema(example = "51234000.00") BigDecimal revenue,
      @Schema(example = "170780.00") BigDecimal averageCheck) {}

  public record DayOrders(
      @Schema(example = "2026-11-20") LocalDate date, @Schema(example = "41") long orders) {}

  public record TopDish(
      @Schema(example = "1") long productId,
      @Schema(example = "Salmon avocado roll") String name,
      @Schema(example = "340") long quantity) {}

  // ---- orders --------------------------------------------------------------------------------

  /** One row of GET /admin/orders. */
  public record AdminOrderSummary(
      @Schema(example = "WR-7K3M9Q2X") String orderNumber,
      Instant createdAt,
      @Schema(example = "2026-11-20") LocalDate orderDate,
      @Schema(type = "string", example = "12:30") @JsonFormat(pattern = "HH:mm")
          LocalTime slotStart,
      @Schema(type = "string", example = "13:00") @JsonFormat(pattern = "HH:mm") LocalTime slotEnd,
      @Schema(example = "UZB") String country,
      @Schema(example = "UZS") String currency,
      Named cafe,
      @Schema(example = "PICKUP") String scenario,
      @Schema(example = "NEW") String status,
      @Schema(example = "Aziz Karimov") String contactName,
      @Schema(example = "+998901234567") String contactPhone,
      @Schema(nullable = true, description = "null for a guest") Account account,
      @Schema(example = "3") int itemCount,
      @Schema(example = "209630.00") BigDecimal total) {}

  /** GET /admin/orders/{orderNumber}: the order of section 3.9 and what only the admin sees. */
  public record AdminOrderResponse(
      OrderResponse order,
      @Schema(nullable = true, example = "aziz@example.test") String contactEmail,
      @Schema(nullable = true, example = "Ring the bell twice") String comment,
      @Schema(nullable = true, description = "null for a guest") Account account,
      Instant updatedAt) {}

  // ---- bookings ------------------------------------------------------------------------------

  /** One row of GET /admin/bookings. */
  public record AdminBookingSummary(
      @Schema(example = "BK-3F8K2M9P") String bookingNumber,
      Instant createdAt,
      @Schema(example = "2026-11-21") LocalDate date,
      @Schema(type = "string", example = "19:30") @JsonFormat(pattern = "HH:mm") LocalTime time,
      @Schema(type = "string", example = "21:00") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
      @Schema(example = "UZB") String country,
      Named cafe,
      @Schema(example = "4") int guests,
      @Schema(example = "4") int tableSeats,
      @Schema(example = "CONFIRMED") String status,
      @Schema(example = "Aziz Karimov") String contactName,
      @Schema(example = "+998901234567") String contactPhone,
      @Schema(nullable = true) String contactEmail,
      @Schema(nullable = true, example = "Birthday dinner") String comment,
      @Schema(nullable = true, description = "null for a guest") Account account) {}

  // ---- users ---------------------------------------------------------------------------------

  /** One row of GET /admin/users. Never the password hash. */
  public record AdminUserSummary(
      @Schema(example = "4") long id,
      @Schema(example = "customer@wokandroll.test") String email,
      @Schema(example = "Demo Customer") String fullName,
      @Schema(nullable = true, example = "+998901234567") String phone,
      @Schema(example = "CUSTOMER") String role,
      Instant createdAt,
      @Schema(example = "84") long orders,
      @Schema(nullable = true, description = "When the last order was placed")
          Instant lastOrderAt) {}

  // ---- cafés ---------------------------------------------------------------------------------

  /** One café of GET /admin/cafes, with its tables and its day. */
  public record AdminCafeResponse(
      @Schema(example = "1") long id,
      @Schema(example = "UZB") String country,
      @Schema(example = "Tashkent") String city,
      @Schema(example = "Wok & Roll Chilanzar") String name,
      @Schema(example = "Bunyodkor Ave 12, Tashkent") String address,
      @Schema(nullable = true, example = "+998712000101") String phone,
      @Schema(type = "string", example = "10:00") @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
      @Schema(type = "string", example = "22:00") @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
      @Schema(example = "Asia/Tashkent") String timeZone,
      @Schema(example = "true") boolean active,
      @Schema(example = "6") int tables,
      @Schema(example = "26") int seats,
      @Schema(example = "5", description = "Orders one 30-minute slot can take") int slotCapacity,
      @Schema(example = "2026-11-24", description = "Today in the café's time zone")
          LocalDate today,
      @Schema(example = "12", description = "Today's orders, without cancelled") long todayOrders,
      @Schema(example = "3", description = "Today's confirmed bookings") long todayBookings) {}

  /** GET /admin/cafes. */
  public record AdminCafeListResponse(List<AdminCafeResponse> items) {}
}
