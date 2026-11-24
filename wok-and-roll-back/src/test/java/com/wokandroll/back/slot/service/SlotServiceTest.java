package com.wokandroll.back.slot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.slot.dto.TimeSlotResponse;
import com.wokandroll.back.slot.repository.SlotRepository;
import com.wokandroll.back.slot.repository.SlotRepository.TakenPlaces;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SlotServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final LocalTime NOON = LocalTime.of(12, 0);

  // Chilanzar from the seed: open 10:00–22:00, 5 orders per slot.
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
          "Asia/Tashkent",
          41.272187,
          69.203167);

  private final CafeRepository cafes = mock(CafeRepository.class);
  private final SlotRepository slots = mock(SlotRepository.class);
  private final CountryClock countryClock = mock(CountryClock.class);
  private final SlotService service = new SlotService(cafes, slots, countryClock);

  @BeforeEach
  void cafeAndClock() {
    when(cafes.findActive(1)).thenReturn(Optional.of(CAFE));
    when(cafes.findActive(99)).thenReturn(Optional.empty());
    when(countryClock.today("UZB")).thenReturn(TODAY);
    when(countryClock.now("UZB")).thenReturn(TODAY.atTime(12, 10));
    when(slots.findTaken(eq(1L), any())).thenReturn(List.of());
  }

  @Test
  void slotsGoFromOpeningToHalfAnHourBeforeClosing() {
    List<TimeSlotResponse> day = service.getSlots(1, TOMORROW).slots();

    assertThat(day).hasSize(24);
    assertThat(day.get(0)).isEqualTo(new TimeSlotResponse("10:00", "10:30", 5, 5, true, null));
    assertThat(day.get(23)).isEqualTo(new TimeSlotResponse("21:30", "22:00", 5, 5, true, null));
    assertThat(service.getSlots(1, TOMORROW).timeZone()).isEqualTo("Asia/Tashkent");
  }

  @Test
  void fullSlotIsNotAvailable() {
    TakenPlaces full = new TakenPlaces(LocalTime.of(10, 30), 5);
    TakenPlaces almostFull = new TakenPlaces(LocalTime.of(21, 30), 4);
    when(slots.findTaken(1, TOMORROW)).thenReturn(List.of(full, almostFull));

    List<TimeSlotResponse> day = service.getSlots(1, TOMORROW).slots();

    assertThat(day.get(1)).isEqualTo(new TimeSlotResponse("10:30", "11:00", 5, 0, false, "FULL"));
    assertThat(day.get(23)).isEqualTo(new TimeSlotResponse("21:30", "22:00", 5, 1, true, null));
  }

  @Test
  void slotThatStartsInLessThanThirtyMinutesIsPast() {
    // Now is 12:10 in Tashkent: 12:30 starts in 20 minutes, 13:00 in 50 minutes.
    List<TimeSlotResponse> day = service.getSlots(1, TODAY).slots();

    assertThat(day.get(0).reason()).isEqualTo("PAST");
    assertThat(day.get(5)).isEqualTo(new TimeSlotResponse("12:30", "13:00", 5, 5, false, "PAST"));
    assertThat(day.get(6)).isEqualTo(new TimeSlotResponse("13:00", "13:30", 5, 5, true, null));
  }

  @Test
  void slotExactlyThirtyMinutesAheadIsStillAvailable() {
    when(countryClock.now("UZB")).thenReturn(TODAY.atTime(12, 30));

    List<TimeSlotResponse> day = service.getSlots(1, TODAY).slots();

    assertThat(day.get(5).reason()).isEqualTo("PAST");
    assertThat(day.get(6).available()).isTrue();
  }

  @Test
  void dateMustBeFromTodayToSevenDaysAhead() {
    assertThat(service.getSlots(1, TODAY.plusDays(7)).slots()).hasSize(24);

    for (LocalDate date : List.of(TODAY.minusDays(1), TODAY.plusDays(8))) {
      ApiException e = assertThrows(ApiException.class, () -> service.getSlots(1, date));
      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
      assertThat(e.getFields()).containsKey("date");
    }
  }

  @Test
  void unknownCafeIsBadRequest() {
    ApiException e = assertThrows(ApiException.class, () -> service.getSlots(99, TOMORROW));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getCode()).isEqualTo("UNKNOWN_CAFE");
    assertThat(e.getFields()).containsKey("cafeId");
  }

  @Test
  void reserveTakesAPlaceWithTheCapacityOfTheCafe() {
    when(slots.reserve(1, TOMORROW, NOON, 5)).thenReturn(true);

    service.reserve(1, TOMORROW, NOON);

    verify(slots).reserve(1, TOMORROW, NOON, 5);
  }

  @Test
  void fullSlotGivesSlotFull() {
    when(slots.reserve(1, TOMORROW, NOON, 5)).thenReturn(false);

    ApiException e = assertThrows(ApiException.class, () -> service.reserve(1, TOMORROW, NOON));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getCode()).isEqualTo("SLOT_FULL");
    assertThat(e.getFields()).containsKey("slotStart");
  }

  @Test
  void reserveRejectsATimeThatIsNotAFreeSlot() {
    List<Runnable> wrong =
        List.of(
            () -> service.reserve(1, TOMORROW, LocalTime.of(12, 15)), // not on the 30-minute grid
            () -> service.reserve(1, TOMORROW, LocalTime.of(22, 0)), // the café closes at 22:00
            () -> service.reserve(1, TOMORROW, LocalTime.of(9, 30)), // before opening
            () -> service.reserve(1, TODAY, LocalTime.of(12, 30))); // starts in 20 minutes

    for (Runnable call : wrong) {
      ApiException e = assertThrows(ApiException.class, call::run);
      assertThat(e.getCode()).isEqualTo("INVALID_SLOT");
      assertThat(e.getFields()).containsKey("slotStart");
    }
    verify(slots, never()).reserve(anyLong(), any(), any(), anyInt());
  }

  @Test
  void reserveInAnUnknownCafeIsBadRequest() {
    ApiException e = assertThrows(ApiException.class, () -> service.reserve(99, TOMORROW, NOON));

    assertThat(e.getCode()).isEqualTo("UNKNOWN_CAFE");
  }

  @Test
  void releaseGivesThePlaceBack() {
    service.release(1, TOMORROW, NOON);

    verify(slots).release(1, TOMORROW, NOON);
  }

  @Test
  void slotStartsNeverGoPastClosingOrMidnight() {
    assertThat(SlotService.slotStarts(LocalTime.of(10, 0), LocalTime.of(11, 0)))
        .containsExactly(LocalTime.of(10, 0), LocalTime.of(10, 30));
    assertThat(SlotService.slotStarts(LocalTime.of(23, 0), LocalTime.of(23, 50)))
        .containsExactly(LocalTime.of(23, 0));
  }
}
