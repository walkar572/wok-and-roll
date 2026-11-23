package com.wokandroll.back.booking.service;

import com.wokandroll.back.booking.dto.BookingRequest;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import com.wokandroll.back.common.error.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * The field rules of a booking (api-contract.md section 3.10). Every problem is collected, so the
 * guest sees all of them at once; the code of the error is the code of the first problem. Pure
 * logic: the café and "now" in the café's time zone are given, so it is easy to test.
 */
public final class BookingValidator {

  static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  static final String UNKNOWN_CAFE = "UNKNOWN_CAFE";
  static final String PAST_DATE = "PAST_DATE";

  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}");
  private static final Pattern PHONE = Pattern.compile("\\+\\d{8,15}");
  private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+");

  private BookingValidator() {}

  /** A booking that passed every rule, with trimmed texts. */
  public record ValidBooking(
      CafeRow cafe,
      LocalDate date,
      LocalTime time,
      int guests,
      String contactName,
      String contactPhone,
      String contactEmail,
      String comment) {}

  /**
   * @param cafe the active café of {@code request.cafeId()}, or null when it is unknown or closed
   * @param now the local date and time in the café's time zone (ignored when cafe is null)
   */
  public static ValidBooking validate(BookingRequest request, CafeRow cafe, LocalDateTime now) {
    Problems problems = new Problems();

    if (request.cafeId() == null) {
      problems.add(VALIDATION_ERROR, "cafeId", "is required");
    } else if (cafe == null) {
      problems.add(
          UNKNOWN_CAFE, "cafeId", "Café " + request.cafeId() + " does not exist or is closed");
    }

    LocalDate date = parseDate(request.date(), problems);
    if (date != null && cafe != null) {
      LocalDate today = now.toLocalDate();
      if (date.isBefore(today)) {
        problems.add(PAST_DATE, "date", "Choose today or a later date");
      } else if (date.isAfter(today.plusDays(BookingRules.MAX_DAYS_AHEAD))) {
        problems.add(
            VALIDATION_ERROR,
            "date",
            "must be at most " + BookingRules.MAX_DAYS_AHEAD + " days ahead");
      }
    }

    LocalTime time = parseTime(request.time(), problems);
    if (time != null && cafe != null) {
      LocalTime last = cafe.closesAt().minusMinutes(BookingRules.DURATION_MINUTES);
      boolean onStep = time.getMinute() % BookingRules.STEP_MINUTES == 0 && time.getSecond() == 0;
      if (!onStep || time.isBefore(cafe.opensAt()) || time.isAfter(last)) {
        problems.add(
            VALIDATION_ERROR,
            "time",
            "must be from " + cafe.opensAt() + " to " + last + " on a 30-minute step");
      } else if (date != null && date.equals(now.toLocalDate())) {
        Duration lead = Duration.between(now, date.atTime(time));
        if (lead.toMinutes() < BookingRules.MIN_LEAD_MINUTES) {
          problems.add(
              PAST_DATE,
              "time",
              "must be at least " + BookingRules.MIN_LEAD_MINUTES + " minutes from now");
        }
      }
    }

    Integer guests = request.guests();
    if (guests == null) {
      problems.add(VALIDATION_ERROR, "guests", "is required");
    } else if (guests > BookingRules.MAX_GUESTS) {
      problems.add(VALIDATION_ERROR, "guests", "For more than 8 guests, please call the café.");
    } else if (guests < BookingRules.MIN_GUESTS) {
      problems.add(VALIDATION_ERROR, "guests", "must be from 1 to 8");
    }

    String name = text("contactName", request.contactName(), 2, 100, true, problems);
    String phone = phone(request.contactPhone(), problems);
    String email = email(request.contactEmail(), problems);
    String comment = text("comment", request.comment(), 0, 300, false, problems);

    problems.throwIfAny();
    return new ValidBooking(cafe, date, time, guests, name, phone, email, comment);
  }

  private static LocalDate parseDate(String raw, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "date", "is required");
      return null;
    }
    try {
      if (DATE.matcher(raw.trim()).matches()) {
        return LocalDate.parse(raw.trim());
      }
    } catch (DateTimeParseException e) {
      // falls through to the message below
    }
    problems.add(VALIDATION_ERROR, "date", "must be a date in the format YYYY-MM-DD");
    return null;
  }

  private static LocalTime parseTime(String raw, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "time", "is required");
      return null;
    }
    try {
      if (TIME.matcher(raw.trim()).matches()) {
        return LocalTime.parse(raw.trim());
      }
    } catch (DateTimeParseException e) {
      // falls through to the message below
    }
    problems.add(VALIDATION_ERROR, "time", "must be a time in the format HH:mm");
    return null;
  }

  private static String text(
      String field, String raw, int min, int max, boolean required, Problems problems) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        problems.add(VALIDATION_ERROR, field, "is required");
      }
      return null;
    }
    if (text.length() < min || text.length() > max) {
      problems.add(
          VALIDATION_ERROR,
          field,
          min > 0
              ? "must be " + min + " to " + max + " characters"
              : "must be " + max + " characters or fewer");
      return null;
    }
    return text;
  }

  private static String phone(String raw, Problems problems) {
    String phone = raw == null ? "" : raw.replaceAll("\\s", "");
    if (phone.isEmpty()) {
      problems.add(VALIDATION_ERROR, "contactPhone", "is required");
      return null;
    }
    if (!PHONE.matcher(phone).matches()) {
      problems.add(
          VALIDATION_ERROR,
          "contactPhone",
          "must be in the international format: + and 8 to 15 digits");
      return null;
    }
    return phone;
  }

  private static String email(String raw, Problems problems) {
    String email = raw == null ? "" : raw.trim();
    if (email.isEmpty()) {
      return null;
    }
    if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
      problems.add(
          VALIDATION_ERROR, "contactEmail", "must be a valid email of 254 characters or fewer");
      return null;
    }
    return email.toLowerCase(Locale.ROOT);
  }

  /** Collects all problems. The first one decides the error code. */
  private static final class Problems {

    private final Map<String, String> fields = new LinkedHashMap<>();
    private String code;

    void add(String problemCode, String field, String message) {
      if (code == null) {
        code = problemCode;
      }
      fields.putIfAbsent(field, message);
    }

    void throwIfAny() {
      if (code != null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, code, "Invalid booking", fields);
      }
    }
  }
}
