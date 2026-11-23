package com.wokandroll.back.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CountryClockTest {

  // 17 Nov 2026, 20:30 UTC: already 18 Nov in Tashkent (UTC+5), still 17 Nov in Kyiv (UTC+2).
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-11-17T20:30:00Z"), ZoneOffset.UTC);

  private final CountryTimeZoneRepository zones = mock(CountryTimeZoneRepository.class);
  private final CountryClock countryClock = new CountryClock(zones, FIXED);

  @BeforeEach
  void timeZonesFromTheSeed() {
    when(zones.find("UZB")).thenReturn(Optional.of(ZoneId.of("Asia/Tashkent")));
    when(zones.find("KAZ")).thenReturn(Optional.of(ZoneId.of("Asia/Almaty")));
    when(zones.find("UKR")).thenReturn(Optional.of(ZoneId.of("Europe/Kyiv")));
    when(zones.find("CHN")).thenReturn(Optional.of(ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void todayDependsOnTheCountry() {
    assertThat(countryClock.today("UZB")).isEqualTo(LocalDate.of(2026, 11, 18));
    assertThat(countryClock.today("UKR")).isEqualTo(LocalDate.of(2026, 11, 17));
  }

  @Test
  void nowIsTheLocalTimeOfTheCountry() {
    assertThat(countryClock.now("UZB")).isEqualTo(LocalDateTime.of(2026, 11, 18, 1, 30));
    assertThat(countryClock.now("UKR")).isEqualTo(LocalDateTime.of(2026, 11, 17, 22, 30));
    assertThat(countryClock.now("CHN")).isEqualTo(LocalDateTime.of(2026, 11, 18, 4, 30));
  }

  @Test
  void almatyIsUtcPlusFiveLikeTashkent() {
    // tz data 2024a or newer (JDK 21.0.3+): all of Kazakhstan is UTC+5 (api-contract.md §1).
    assertThat(countryClock.now("KAZ")).isEqualTo(countryClock.now("UZB"));
  }

  @Test
  void zoneComesFromTheRepository() {
    assertThat(countryClock.zone("UZB")).isEqualTo(ZoneId.of("Asia/Tashkent"));
  }

  @Test
  void unknownCountryIsAnError() {
    when(zones.find("USA")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> countryClock.today("USA"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown country: USA");
  }
}
