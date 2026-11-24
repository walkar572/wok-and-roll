package com.wokandroll.back.country.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.country.repository.CountryRepository;
import com.wokandroll.back.country.repository.CountryRepository.CountryRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CountryServiceTest {

  private final CountryRepository repository = mock(CountryRepository.class);
  private final CountryService service = new CountryService(repository);

  @Test
  void keepsTheOrderAndGivesTheVatRateTwoDecimals() {
    CountryRow uzb =
        new CountryRow(
            "UZB", "Uzbekistan", "UZS", new BigDecimal("15"), "Asia/Tashkent", List.of("Tashkent"));
    CountryRow ukr =
        new CountryRow("UKR", "Ukraine", "UAH", new BigDecimal("8.0"), "Europe/Kyiv", List.of());
    when(repository.findAll()).thenReturn(List.of(uzb, ukr));

    List<CountryResponse> result = service.getCountries();

    assertThat(result).extracting(CountryResponse::code).containsExactly("UZB", "UKR");
    assertThat(result.get(0).name()).isEqualTo("Uzbekistan");
    assertThat(result.get(0).currency()).isEqualTo("UZS");
    assertThat(result.get(0).vatRate().toPlainString()).isEqualTo("15.00");
    assertThat(result.get(1).vatRate().toPlainString()).isEqualTo("8.00");
    assertThat(result.get(0).timeZone()).isEqualTo("Asia/Tashkent");
    assertThat(result.get(1).timeZone()).isEqualTo("Europe/Kyiv");
    assertThat(result.get(0).cities()).containsExactly("Tashkent");
    assertThat(result.get(1).cities()).isEmpty();
  }
}
