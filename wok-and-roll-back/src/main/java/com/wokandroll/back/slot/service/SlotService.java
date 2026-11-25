package com.wokandroll.back.slot.service;

import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.slot.dto.TimeSlotResponse;
import com.wokandroll.back.slot.dto.TimeSlotsResponse;
import com.wokandroll.back.slot.repository.SlotRepository;
import com.wokandroll.back.slot.repository.SlotRepository.TakenPlaces;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Time slots of a café (api-contract.md 3.8, #148). A slot is 30 minutes. The first slot starts
 * when the café opens, the last one 30 minutes before it closes. All times are local times of the
 * café's country. Only taken places are stored; the list of slots is calculated.
 */
@Service
public class SlotService {

  static final Duration SLOT_LENGTH = Duration.ofMinutes(30);

  /** A slot that starts in less than this time from now cannot be chosen any more. */
  static final Duration MIN_LEAD_TIME = Duration.ofMinutes(30);

  /** The guest can choose a date from today to this many days ahead. */
  static final int MAX_DAYS_AHEAD = 7;

  static final String FULL = "FULL";
  static final String PAST = "PAST";

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final CafeRepository cafes;
  private final SlotRepository slots;
  private final CountryClock countryClock;

  public SlotService(CafeRepository cafes, SlotRepository slots, CountryClock countryClock) {
    this.cafes = cafes;
    this.slots = slots;
    this.countryClock = countryClock;
  }

  /** All slots of the café on that date with their free places (GET /api/v1/time-slots). */
  public TimeSlotsResponse getSlots(long cafeId, LocalDate date) {
    CafeRow cafe = findCafe(cafeId);
    LocalDate today = countryClock.today(cafe.countryCode());
    if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Request validation failed",
          Map.of("date", "must be from today to " + MAX_DAYS_AHEAD + " days ahead"));
    }

    Map<LocalTime, Integer> taken = new HashMap<>();
    for (TakenPlaces places : slots.findTaken(cafeId, date)) {
      taken.put(places.slotStart(), places.reserved());
    }
    LocalDateTime now = countryClock.now(cafe.countryCode());

    List<TimeSlotResponse> items = new ArrayList<>();
    for (LocalTime start : slotStarts(cafe.opensAt(), cafe.closesAt())) {
      int remaining = Math.max(0, cafe.slotCapacity() - taken.getOrDefault(start, 0));
      String reason = null;
      if (startsTooSoon(date, start, now)) {
        reason = PAST;
      } else if (remaining == 0) {
        reason = FULL;
      }
      items.add(
          new TimeSlotResponse(
              start.format(HH_MM),
              start.plus(SLOT_LENGTH).format(HH_MM),
              cafe.slotCapacity(),
              remaining,
              reason == null,
              reason));
    }
    return new TimeSlotsResponse(cafeId, date, cafe.timeZone(), items);
  }

  /**
   * Takes one place in the slot. POST /orders calls it inside its transaction, so the place is free
   * again if the order fails later. One atomic statement: two guests can never take the same last
   * place.
   *
   * @throws ApiException 400 UNKNOWN_CAFE, 400 INVALID_SLOT (not a slot of that day, or it starts
   *     in less than 30 minutes), 409 SLOT_FULL
   */
  public void reserve(long cafeId, LocalDate date, LocalTime slotStart) {
    CafeRow cafe = findCafe(cafeId);
    boolean isSlot = slotStarts(cafe.opensAt(), cafe.closesAt()).contains(slotStart);
    if (!isSlot || startsTooSoon(date, slotStart, countryClock.now(cafe.countryCode()))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SLOT",
          "This time is not a free slot of the café",
          Map.of("slotStart", "Please choose one of the offered times."));
    }
    if (!slots.reserve(cafeId, date, slotStart, cafe.slotCapacity())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SLOT_FULL",
          "This time slot is full",
          Map.of("slotStart", "This time slot is full. Please choose another time."));
    }
  }

  /** Gives the place back when an order is cancelled (contract 3.14). Never below 0. */
  public void release(long cafeId, LocalDate date, LocalTime slotStart) {
    slots.release(cafeId, date, slotStart);
  }

  /** 10:00, 10:30, … 21:30 for a café open 10:00–22:00. */
  static List<LocalTime> slotStarts(LocalTime opensAt, LocalTime closesAt) {
    List<LocalTime> starts = new ArrayList<>();
    LocalTime start = opensAt;
    // Compare durations, not times: LocalTime.plus wraps around midnight.
    while (Duration.between(start, closesAt).compareTo(SLOT_LENGTH) >= 0) {
      starts.add(start);
      start = start.plus(SLOT_LENGTH);
    }
    return starts;
  }

  /** True when the slot has started or starts in less than 30 minutes. */
  private static boolean startsTooSoon(LocalDate date, LocalTime start, LocalDateTime now) {
    return date.atTime(start).isBefore(now.plus(MIN_LEAD_TIME));
  }

  private CafeRow findCafe(long cafeId) {
    return cafes
        .findActive(cafeId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNKNOWN_CAFE",
                    "Unknown café",
                    Map.of("cafeId", "Café " + cafeId + " does not exist or is closed")));
  }
}
