package com.wokandroll.back.staff;

import com.wokandroll.back.booking.service.BookingRules;
import com.wokandroll.back.staff.StaffDtos.StaffBookingResponse;
import com.wokandroll.back.staff.StaffDtos.StaffOrderResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL of the staff API (#176). */
@Repository
public class StaffRepository {

  private final JdbcClient jdbc;

  public StaffRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<StaffOrderResponse> findOrders(long cafeId, LocalDate date, int limit, int offset) {
    return jdbc.sql(
            """
            SELECT o.order_number, o.slot_start, o.slot_end, o.scenario, o.status, o.contact_name,
                   o.contact_phone, o.delivery_address, o.total, o.currency_code,
                   (SELECT coalesce(sum(i.quantity), 0) FROM order_item i WHERE i.order_id = o.id)
                     AS item_count
            FROM customer_order o
            WHERE o.cafe_id = :cafeId AND o.order_date = :date
            ORDER BY o.slot_start, o.order_number
            LIMIT :limit OFFSET :offset
            """)
        .param("cafeId", cafeId)
        .param("date", date)
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) ->
                new StaffOrderResponse(
                    rs.getString("order_number"),
                    rs.getObject("slot_start", LocalTime.class),
                    rs.getObject("slot_end", LocalTime.class),
                    rs.getString("scenario"),
                    rs.getString("status"),
                    rs.getString("contact_name"),
                    rs.getString("contact_phone"),
                    rs.getString("delivery_address"),
                    rs.getInt("item_count"),
                    rs.getBigDecimal("total"),
                    rs.getString("currency_code").trim()))
        .list();
  }

  /** The status, café, date and slot of an order, locked for the status change. */
  public record OrderState(String status, long cafeId, LocalDate orderDate, LocalTime slotStart) {}

  public Optional<OrderState> lockOrder(String orderNumber) {
    return jdbc.sql(
            """
            SELECT status, cafe_id, order_date, slot_start
            FROM customer_order WHERE order_number = :number
            FOR UPDATE
            """)
        .param("number", orderNumber)
        .query(
            (rs, i) ->
                new OrderState(
                    rs.getString("status"),
                    rs.getLong("cafe_id"),
                    rs.getObject("order_date", LocalDate.class),
                    rs.getObject("slot_start", LocalTime.class)))
        .optional();
  }

  public void setStatus(String orderNumber, String status) {
    jdbc.sql(
            """
            UPDATE customer_order SET status = :status, updated_at = now()
            WHERE order_number = :number
            """)
        .param("status", status)
        .param("number", orderNumber)
        .update();
  }

  public List<StaffBookingResponse> findBookings(long cafeId, LocalDate date) {
    return jdbc.sql(
            """
            SELECT b.booking_number, b.booking_time, b.guests, t.seats, b.status, b.contact_name,
                   b.contact_phone
            FROM booking b
            JOIN cafe_table t ON t.id = b.table_id
            WHERE b.cafe_id = :cafeId AND b.booking_date = :date
            ORDER BY b.booking_time, b.booking_number
            """)
        .param("cafeId", cafeId)
        .param("date", date)
        .query(
            (rs, i) -> {
              LocalTime time = rs.getObject("booking_time", LocalTime.class);
              return new StaffBookingResponse(
                  rs.getString("booking_number"),
                  time,
                  time.plusMinutes(BookingRules.DURATION_MINUTES),
                  rs.getInt("guests"),
                  rs.getInt("seats"),
                  rs.getString("status"),
                  rs.getString("contact_name"),
                  rs.getString("contact_phone"));
            })
        .list();
  }
}
