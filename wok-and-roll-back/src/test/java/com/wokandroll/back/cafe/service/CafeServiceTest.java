package com.wokandroll.back.cafe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wokandroll.back.cafe.dto.CafeResponse;
import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CafeServiceTest {

  private final CafeRepository repository = mock(CafeRepository.class);
  private final CafeService service = new CafeService(repository);

  private static CafeRow cafe(long id, String name, String phone, LocalTime opensAt) {
    return new CafeRow(
        id,
        "KAZ",
        name,
        "Almaty",
        "Almaty",
        phone,
        opensAt,
        LocalTime.of(22, 0),
        5,
        "Asia/Almaty",
        43.235697,
        76.874601);
  }

  @Test
  void keepsTheOrderAndGivesTheHoursAsHourAndMinute() {
    CafeRow bostandyk = cafe(4, "Wok & Roll Bostandyk", "+77272000002", LocalTime.of(9, 30));
    CafeRow medeu = cafe(3, "Wok & Roll Medeu", null, LocalTime.of(10, 0));
    when(repository.findAllActive("KAZ")).thenReturn(List.of(bostandyk, medeu));

    List<CafeResponse> items = service.getCafes("KAZ").items();

    assertThat(items)
        .extracting(CafeResponse::name)
        .containsExactly("Wok & Roll Bostandyk", "Wok & Roll Medeu");
    assertThat(items.get(0).id()).isEqualTo(4);
    assertThat(items.get(0).country()).isEqualTo("KAZ");
    assertThat(items.get(0).opensAt()).isEqualTo("09:30");
    assertThat(items.get(1).opensAt()).isEqualTo("10:00");
    assertThat(items.get(0).closesAt()).isEqualTo("22:00");
    assertThat(items.get(0).timeZone()).isEqualTo("Asia/Almaty");
    assertThat(items.get(1).phone()).isNull();
  }

  @Test
  void givesThePlaceOnTheMapAndKeepsAnUnknownPlaceEmpty() {
    CafeRow bostandyk = cafe(4, "Wok & Roll Bostandyk", null, LocalTime.of(10, 0));
    CafeRow newCafe =
        new CafeRow(
            11,
            "KAZ",
            "Wok & Roll Esentai",
            "Almaty",
            "Almaty",
            null,
            LocalTime.of(10, 0),
            LocalTime.of(22, 0),
            5,
            "Asia/Almaty",
            null,
            null);
    when(repository.findAllActive("KAZ")).thenReturn(List.of(bostandyk, newCafe));

    List<CafeResponse> items = service.getCafes("KAZ").items();

    assertThat(items.get(0).latitude()).isEqualTo(43.235697);
    assertThat(items.get(0).longitude()).isEqualTo(76.874601);
    assertThat(items.get(1).latitude()).isNull();
    assertThat(items.get(1).longitude()).isNull();
  }

  @Test
  void countryWithoutCafesGivesAnEmptyList() {
    when(repository.findAllActive("GEO")).thenReturn(List.of());

    assertThat(service.getCafes("GEO").items()).isEmpty();
  }
}
