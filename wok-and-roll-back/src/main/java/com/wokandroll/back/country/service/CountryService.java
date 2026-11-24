package com.wokandroll.back.country.service;

import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.country.repository.CountryRepository;
import com.wokandroll.back.country.repository.CountryRepository.CountryRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** Countries for the switcher (#50): code, name, currency, VAT rate, time zone and cities. */
@Service
public class CountryService {

  private final CountryRepository repository;

  public CountryService(CountryRepository repository) {
    this.repository = repository;
  }

  public List<CountryResponse> getCountries() {
    return repository.findAll().stream().map(CountryService::toResponse).toList();
  }

  private static CountryResponse toResponse(CountryRow c) {
    return new CountryResponse(
        c.code(),
        c.name(),
        c.currency(),
        twoDecimals(c.vatRate()),
        c.timeZone(),
        List.copyOf(c.cities()));
  }

  /** Rates always have exactly 2 decimals in the JSON (api-contract.md section 1). */
  private static BigDecimal twoDecimals(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
