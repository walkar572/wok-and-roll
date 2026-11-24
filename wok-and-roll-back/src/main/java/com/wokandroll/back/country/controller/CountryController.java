package com.wokandroll.back.country.controller;

import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.country.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/countries: the country switcher (#105, api-contract.md 3.1). */
@RestController
@RequestMapping("/api/v1/countries")
@Tag(name = "Countries")
public class CountryController {

  private final CountryService service;

  public CountryController(CountryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "All countries with their currency and VAT rate",
      description = "Used by the country switcher. Order as in the seed data, UZB first.")
  @ApiResponse(responseCode = "200", description = "The 6 countries")
  public List<CountryResponse> getCountries() {
    return service.getCountries();
  }
}
