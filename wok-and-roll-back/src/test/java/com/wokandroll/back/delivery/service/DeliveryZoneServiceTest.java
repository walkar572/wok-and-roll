package com.wokandroll.back.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wokandroll.back.delivery.dto.DeliveryZoneResponse;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryZoneServiceTest {

  private final DeliveryZoneRepository repository = mock(DeliveryZoneRepository.class);
  private final DeliveryZoneService service = new DeliveryZoneService(repository);

  @Test
  void keepsTheOrderAndGivesTheFeeTwoDecimals() {
    DeliveryZoneRow center =
        new DeliveryZoneRow(1, "UZB", "Tashkent: Center", 1, new BigDecimal("20"), "UZS", 45);
    DeliveryZoneRow yunusabad =
        new DeliveryZoneRow(2, "UZB", "Tashkent: Yunusabad", 2, new BigDecimal("15.0"), "UZS", 40);
    when(repository.findAllActive("UZB")).thenReturn(List.of(center, yunusabad));

    List<DeliveryZoneResponse> items = service.getZones("UZB").items();

    assertThat(items)
        .extracting(DeliveryZoneResponse::name)
        .containsExactly("Tashkent: Center", "Tashkent: Yunusabad");
    assertThat(items.get(0).fee().toPlainString()).isEqualTo("20.00");
    assertThat(items.get(1).fee().toPlainString()).isEqualTo("15.00");
    assertThat(items.get(0).id()).isEqualTo(1);
    assertThat(items.get(0).country()).isEqualTo("UZB");
    assertThat(items.get(0).cafeId()).isEqualTo(1);
    assertThat(items.get(1).cafeId()).isEqualTo(2);
    assertThat(items.get(0).currency()).isEqualTo("UZS");
    assertThat(items.get(1).deliveryMinutes()).isEqualTo(40);
  }

  @Test
  void countryWithoutZonesGivesAnEmptyList() {
    when(repository.findAllActive("GEO")).thenReturn(List.of());

    assertThat(service.getZones("GEO").items()).isEmpty();
  }
}
