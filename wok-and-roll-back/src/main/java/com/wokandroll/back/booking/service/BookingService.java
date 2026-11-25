package com.wokandroll.back.booking.service;

import com.wokandroll.back.booking.dto.BookingRequest;
import com.wokandroll.back.booking.dto.BookingResponse;
import com.wokandroll.back.booking.repository.BookingRepository;
import com.wokandroll.back.booking.repository.BookingRepository.NewBooking;
import com.wokandroll.back.booking.service.BookingValidator.ValidBooking;
import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.order.service.OrderNumberGenerator;
import com.wokandroll.back.order.service.RequestHasher;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Books a table (api-contract.md section 3.10, #174). The insert picks the smallest free table in
 * one statement; the exclusion constraint of the database makes double booking impossible. When
 * another guest wins the same table at the same moment (SQLSTATE 23P01), the next free table is
 * tried. Each try is one statement with its own commit, so a refused try does not break anything.
 */
@Service
public class BookingService {

  static final String PREFIX = "BK-";
  static final int MAX_TRIES = 5;

  private static final String EXCLUSION_VIOLATION = "23P01";
  private static final String UNIQUE_VIOLATION = "23505";

  private final BookingRepository bookings;
  private final CafeRepository cafes;
  private final CountryClock clock;
  private final OrderNumberGenerator numbers;
  private final RequestHasher hasher;

  public BookingService(
      BookingRepository bookings,
      CafeRepository cafes,
      CountryClock clock,
      OrderNumberGenerator numbers,
      RequestHasher hasher) {
    this.bookings = bookings;
    this.cafes = cafes;
    this.clock = clock;
    this.numbers = numbers;
    this.hasher = hasher;
  }

  /** The booking and whether it is the replay of an earlier request with the same key. */
  public record Result(BookingResponse booking, boolean replayed) {}

  public Result book(BookingRequest request, UUID key, Long userId) {
    String hash = hasher.hash(request);
    if (key != null) {
      Optional<Result> replay = replay(key, hash);
      if (replay.isPresent()) {
        return replay.get();
      }
    }

    CafeRow cafe =
        request.cafeId() == null ? null : cafes.findActive(request.cafeId()).orElse(null);
    LocalDateTime now = cafe == null ? null : clock.now(cafe.countryCode());
    ValidBooking valid = BookingValidator.validate(request, cafe, now);

    ZoneId zone = ZoneId.of(valid.cafe().timeZone());
    OffsetDateTime start = valid.date().atTime(valid.time()).atZone(zone).toOffsetDateTime();
    OffsetDateTime end = start.plusMinutes(BookingRules.DURATION_MINUTES);

    for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
      String number = numbers.next(PREFIX);
      try {
        Optional<Long> id =
            bookings.insertIntoSmallestFreeTable(
                new NewBooking(
                    number,
                    userId,
                    valid.cafe().id(),
                    valid.date(),
                    valid.time(),
                    valid.guests(),
                    start,
                    end,
                    valid.contactName(),
                    valid.contactPhone(),
                    valid.contactEmail(),
                    valid.comment(),
                    key,
                    hash));
        if (id.isEmpty()) {
          throw noTable(valid);
        }
        return new Result(bookings.findResponse(number).orElseThrow(), false);
      } catch (DataIntegrityViolationException e) {
        String state = sqlState(e);
        if (UNIQUE_VIOLATION.equals(state) && key != null) {
          // The same key arrived twice at the same moment: the other request saved it.
          Optional<Result> replay = replay(key, hash);
          if (replay.isPresent()) {
            return replay.get();
          }
        }
        if (!EXCLUSION_VIOLATION.equals(state) && !UNIQUE_VIOLATION.equals(state)) {
          throw e;
        }
        // 23P01: another guest took that table just now; 23505: the number was taken. Try again.
      }
    }
    throw noTable(valid);
  }

  private Optional<Result> replay(UUID key, String hash) {
    return bookings
        .findByKey(key)
        .map(
            saved -> {
              if (!saved.requestHash().equals(hash)) {
                throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was used for another booking",
                    Map.of("Idempotency-Key", "was used for another booking"));
              }
              return new Result(bookings.findResponse(saved.bookingNumber()).orElseThrow(), true);
            });
  }

  private static ApiException noTable(ValidBooking valid) {
    String message =
        "No table for "
            + valid.guests()
            + (valid.guests() == 1 ? " guest" : " guests")
            + " at "
            + valid.time()
            + ". Please choose another time.";
    return new ApiException(
        HttpStatus.CONFLICT, "NO_TABLE_AVAILABLE", message, Map.of("time", message));
  }

  private static String sqlState(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sql.getSQLState() != null) {
        return sql.getSQLState();
      }
    }
    return null;
  }
}
