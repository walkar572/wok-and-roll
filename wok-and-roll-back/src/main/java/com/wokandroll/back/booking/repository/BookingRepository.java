package com.wokandroll.back.booking.repository;

import com.wokandroll.back.booking.dto.BookingResponse;
import com.wokandroll.back.booking.service.BookingRules;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL of the table booking (#174, api-contract.md section 3.10). */
@Repository
public class BookingRepository {

  /**
   * Takes the smallest free table with enough seats in one statement. "Free" means no confirmed
   * booking of the table overlaps [start, end). If another guest takes the same table at the same
   * moment, the exclusion constraint ex_booking_table_time refuses one insert (SQLSTATE 23P01) and
   * the service tries again: then this table is no longer free and the next one is taken.
   */
  private static final String INSERT_SQL =
      """
      INSERT INTO booking (booking_number, user_id, cafe_id, table_id, booking_date, booking_time,
                           guests, during, contact_name, contact_phone, contact_email, comment,
                           idempotency_key, request_hash)
      SELECT :number, :userId, :cafeId, t.id, :date, :time, :guests,
             tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'),
             :contactName, :contactPhone, :contactEmail, :comment, CAST(:key AS uuid), :hash
      FROM cafe_table t
      WHERE t.cafe_id = :cafeId
        AND t.seats >= :guests
        AND NOT EXISTS (
          SELECT 1 FROM booking b
          WHERE b.table_id = t.id
            AND b.status = 'CONFIRMED'
            AND b.during && tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'))
      ORDER BY t.seats, t.id
      LIMIT 1
      RETURNING id
      """;

  private static final String BY_NUMBER_SQL =
      """
      SELECT b.booking_number, b.status, b.created_at, cf.id AS cafe_id, cf.name AS cafe_name,
             cf.address AS cafe_address, b.booking_date, b.booking_time, b.guests, t.seats,
             b.contact_name
      FROM booking b
      JOIN cafe cf ON cf.id = b.cafe_id
      JOIN cafe_table t ON t.id = b.table_id
      WHERE b.booking_number = :number
      """;

  private final JdbcClient jdbc;

  public BookingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** A booking to insert. Start and end are moments, built in the café's time zone. */
  public record NewBooking(
      String number,
      Long userId,
      long cafeId,
      LocalDate date,
      LocalTime time,
      int guests,
      OffsetDateTime start,
      OffsetDateTime end,
      String contactName,
      String contactPhone,
      String contactEmail,
      String comment,
      UUID idempotencyKey,
      String requestHash) {}

  /** The id of the new booking, or empty when no table is free. */
  public Optional<Long> insertIntoSmallestFreeTable(NewBooking b) {
    return jdbc.sql(INSERT_SQL)
        .param("number", b.number())
        .param("userId", b.userId())
        .param("cafeId", b.cafeId())
        .param("date", b.date())
        .param("time", b.time())
        .param("guests", b.guests())
        .param("start", b.start())
        .param("end", b.end())
        .param("contactName", b.contactName())
        .param("contactPhone", b.contactPhone())
        .param("contactEmail", b.contactEmail())
        .param("comment", b.comment())
        .param("key", b.idempotencyKey() == null ? null : b.idempotencyKey().toString())
        .param("hash", b.requestHash())
        .query(Long.class)
        .optional();
  }

  /** The number and request hash of a booking saved with this idempotency key. */
  public Optional<Saved> findByKey(UUID key) {
    return jdbc.sql(
            "SELECT booking_number, request_hash FROM booking WHERE idempotency_key ="
                + " CAST(:key AS uuid)")
        .param("key", key.toString())
        .query((rs, i) -> new Saved(rs.getString("booking_number"), rs.getString("request_hash")))
        .optional();
  }

  /** A saved booking: its number and the hash of its request. */
  public record Saved(String bookingNumber, String requestHash) {}

  /** True when the number is already used (a new random number is then drawn). */
  public boolean numberExists(String number) {
    return jdbc.sql("SELECT count(*) FROM booking WHERE booking_number = :number")
            .param("number", number)
            .query(Long.class)
            .single()
        > 0;
  }

  public Optional<BookingResponse> findResponse(String number) {
    return jdbc.sql(BY_NUMBER_SQL)
        .param("number", number)
        .query(BookingRepository::toResponse)
        .optional();
  }

  private static BookingResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
    LocalTime time = rs.getObject("booking_time", LocalTime.class);
    return new BookingResponse(
        rs.getString("booking_number"),
        rs.getString("status"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        new BookingResponse.CafeSummary(
            rs.getLong("cafe_id"), rs.getString("cafe_name"), rs.getString("cafe_address")),
        rs.getObject("booking_date", LocalDate.class),
        time,
        time.plusMinutes(BookingRules.DURATION_MINUTES),
        rs.getInt("guests"),
        rs.getInt("seats"),
        rs.getString("contact_name"));
  }
}
