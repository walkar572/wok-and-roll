package com.wokandroll.back.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** "Today" and "now" in the time zone of a country (api-contract.md §1, #143). */
// Use it instead of LocalDate.now() or LocalDateTime.now(): they take the zone of the server.
// Order dates (Timur), time slots and bookings all ask this class, so there is only one helper.
@Component
public class CountryClock {

  private final CountryTimeZoneRepository zones;
  private final Clock clock;

  @Autowired
  public CountryClock(CountryTimeZoneRepository zones) {
    this(zones, Clock.systemUTC());
  }

  /** For tests: a fixed Clock makes "now" known. */
  CountryClock(CountryTimeZoneRepository zones, Clock clock) {
    this.zones = zones;
    this.clock = clock;
  }

  /** The IANA time zone of the country, for example Asia/Tashkent for UZB. */
  public ZoneId zone(String countryCode) {
    return zones
        .find(countryCode)
        .orElseThrow(() -> new IllegalArgumentException("Unknown country: " + countryCode));
  }

  /** Today's date in the country. */
  public LocalDate today(String countryCode) {
    return LocalDate.now(clock.withZone(zone(countryCode)));
  }

  /** The local date and time in the country now. Compare it with local slot times. */
  public LocalDateTime now(String countryCode) {
    return LocalDateTime.now(clock.withZone(zone(countryCode)));
  }
}
