package com.wokandroll.back.order.repository;

import com.wokandroll.back.order.service.SlotReservations;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY adapter (until #148): the atomic statement of api-contract.md §3.8. One statement, so
 * two guests can never both get the last place. No row returned means the slot is full.
 */
@Component
class JdbcSlotReservations implements SlotReservations {

  private static final String RESERVE_SQL =
      """
      INSERT INTO slot_reservation (cafe_id, slot_date, slot_start, reserved)
      VALUES (:cafeId, :slotDate, :slotStart, 1)
      ON CONFLICT (cafe_id, slot_date, slot_start)
      DO UPDATE SET reserved = slot_reservation.reserved + 1
      WHERE slot_reservation.reserved < :capacity
      RETURNING reserved
      """;

  private final JdbcClient jdbc;

  JdbcSlotReservations(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean reserve(long cafeId, LocalDate slotDate, LocalTime slotStart, int capacity) {
    return jdbc.sql(RESERVE_SQL)
        .param("cafeId", cafeId)
        .param("slotDate", slotDate)
        .param("slotStart", slotStart)
        .param("capacity", capacity)
        .query(Integer.class)
        .optional()
        .isPresent();
  }
}
