package com.wokandroll.back.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.wokandroll.back.booking.dto.BookingRequest;
import com.wokandroll.back.booking.service.BookingValidator.ValidBooking;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import com.wokandroll.back.common.error.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** The booking rules of api-contract.md section 3.10 with a fixed "now". */
class BookingValidatorTest {

  private static final CafeRow CAFE =
      new CafeRow(
          1,
          "UZB",
          "Wok & Roll Chilanzar",
          "12 Bunyodkor Ave, Tashkent",
          "+998712000001",
          LocalTime.of(10, 0),
          LocalTime.of(22, 0),
          5,
          "Asia/Tashkent");

  /** Friday 20 Nov 2026, 17:10 in Tashkent. */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 17, 10);

  private static BookingRequest request(String date, String time, Integer guests) {
    return new BookingRequest(
        1L, date, time, guests, " Aziz Karimov ", "+998 90 123 45 67", "Aziz@Mail.UZ", null);
  }

  @Test
  void aValidBookingIsTrimmedAndNormalised() {
    ValidBooking valid = BookingValidator.validate(request("2026-11-21", "19:30", 4), CAFE, NOW);

    assertThat(valid.date()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(valid.time()).isEqualTo(LocalTime.of(19, 30));
    assertThat(valid.guests()).isEqualTo(4);
    assertThat(valid.contactName()).isEqualTo("Aziz Karimov");
    assertThat(valid.contactPhone()).isEqualTo("+998901234567");
    assertThat(valid.contactEmail()).isEqualTo("aziz@mail.uz");
  }

  @Test
  void theLastTimeIsNinetyMinutesBeforeClosing() {
    BookingValidator.validate(request("2026-11-21", "20:30", 2), CAFE, NOW);

    ApiException e =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-11-21", "21:00", 2), CAFE, NOW));
    assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(e.getFields()).containsKey("time");
  }

  @Test
  void timesAreOnAThirtyMinuteStepWithinOpeningHours() {
    for (String time : new String[] {"19:15", "09:30", "25:00"}) {
      ApiException e =
          catchThrowableOfType(
              ApiException.class,
              () -> BookingValidator.validate(request("2026-11-21", time, 2), CAFE, NOW));
      assertThat(e.getFields()).containsKey("time");
    }
  }

  @Test
  void todayTheBookingMustBeAtLeastSixtyMinutesAhead() {
    BookingValidator.validate(request("2026-11-20", "18:30", 2), CAFE, NOW);

    ApiException e =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-11-20", "18:00", 2), CAFE, NOW));
    assertThat(e.getCode()).isEqualTo("PAST_DATE");
    assertThat(e.getFields()).containsKey("time");
  }

  @Test
  void datesFromTodayToThirtyDaysAhead() {
    BookingValidator.validate(request("2026-12-20", "12:00", 2), CAFE, NOW);

    ApiException past =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-11-19", "12:00", 2), CAFE, NOW));
    assertThat(past.getCode()).isEqualTo("PAST_DATE");

    ApiException far =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-12-21", "12:00", 2), CAFE, NOW));
    assertThat(far.getCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(far.getFields()).containsKey("date");
  }

  @Test
  void guestsFromOneToEight() {
    ApiException many =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-11-21", "19:30", 9), CAFE, NOW));
    assertThat(many.getFields().get("guests")).contains("please call the café");

    ApiException none =
        catchThrowableOfType(
            ApiException.class,
            () -> BookingValidator.validate(request("2026-11-21", "19:30", 0), CAFE, NOW));
    assertThat(none.getFields()).containsKey("guests");
  }

  @Test
  void allProblemsComeTogetherAndTheFirstDecidesTheCode() {
    BookingRequest bad = new BookingRequest(99L, "21-11-2026", "7pm", null, "A", "123", "x@", null);

    ApiException e =
        catchThrowableOfType(ApiException.class, () -> BookingValidator.validate(bad, null, null));

    assertThat(e.getCode()).isEqualTo("UNKNOWN_CAFE");
    assertThat(e.getFields())
        .containsKeys("cafeId", "date", "time", "guests", "contactName", "contactPhone")
        .containsKey("contactEmail");
  }
}
