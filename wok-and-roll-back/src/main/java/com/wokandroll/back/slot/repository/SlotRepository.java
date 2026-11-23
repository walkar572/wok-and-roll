package com.wokandroll.back.slot.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Taken places per café and 30-minute slot, table slot_reservation (api-contract.md 3.8). */
@Repository
public class SlotRepository {

  /** Places taken in one slot. A slot without a row has 0 places taken. */
  public record TakenPlaces(LocalTime slotStart, int reserved) {}

  private final JdbcClient jdbc;

  public SlotRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Takes one place with one atomic statement (contract 3.8). Returns false when {@code capacity}
   * places are already taken: then the WHERE of the update is false and no row comes back. Two
   * guests can never take the same last place, because PostgreSQL locks the row for the update.
   */
  public boolean reserve(long cafeId, LocalDate date, LocalTime slotStart, int capacity) {
    return jdbc.sql(
            """
            INSERT INTO slot_reservation (cafe_id, slot_date, slot_start, reserved)
            VALUES (:cafeId, :slotDate, :slotStart, 1)
            ON CONFLICT (cafe_id, slot_date, slot_start)
            DO UPDATE SET reserved = slot_reservation.reserved + 1
            WHERE slot_reservation.reserved < :capacity
            RETURNING reserved
            """)
        .param("cafeId", cafeId)
        .param("slotDate", date)
        .param("slotStart", slotStart)
        .param("capacity", capacity)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  /** Gives one place back (a cancelled order). Never goes below 0; an unknown slot is ignored. */
  public void release(long cafeId, LocalDate date, LocalTime slotStart) {
    jdbc.sql(
            """
            UPDATE slot_reservation
            SET reserved = reserved - 1
            WHERE cafe_id = :cafeId AND slot_date = :slotDate AND slot_start = :slotStart
              AND reserved > 0
            """)
        .param("cafeId", cafeId)
        .param("slotDate", date)
        .param("slotStart", slotStart)
        .update();
  }

  /** Taken places of one café on one day. Slots without taken places are not in the list. */
  public List<TakenPlaces> findTaken(long cafeId, LocalDate date) {
    return jdbc.sql(
            """
            SELECT slot_start, reserved
            FROM slot_reservation
            WHERE cafe_id = :cafeId AND slot_date = :slotDate
            """)
        .param("cafeId", cafeId)
        .param("slotDate", date)
        .query(SlotRepository::toTaken)
        .list();
  }

  private static TakenPlaces toTaken(ResultSet rs, int rowNum) throws SQLException {
    return new TakenPlaces(rs.getObject("slot_start", LocalTime.class), rs.getInt("reserved"));
  }
}
