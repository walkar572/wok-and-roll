package com.wokandroll.back.country.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.country.dto.CountryResponse;
import com.wokandroll.back.country.service.CountryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(CountryController.class)
class CountryControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CountryService service;

  @Test
  void returnsTheShapeFromTheContract() throws Exception {
    CountryResponse uzb =
        new CountryResponse(
            "UZB",
            "Uzbekistan",
            "UZS",
            new BigDecimal("15.00"),
            "Asia/Tashkent",
            List.of("Tashkent"));
    CountryResponse ukr =
        new CountryResponse(
            "UKR", "Ukraine", "UAH", new BigDecimal("8.00"), "Europe/Kyiv", List.of("Kyiv"));
    when(service.getCountries()).thenReturn(List.of(uzb, ukr));

    mvc.perform(get("/api/v1/countries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].code").value("UZB"))
        .andExpect(jsonPath("$[0].name").value("Uzbekistan"))
        .andExpect(jsonPath("$[0].currency").value("UZS"))
        .andExpect(jsonPath("$[0].vatRate").value(15.00))
        .andExpect(jsonPath("$[0].timeZone").value("Asia/Tashkent"))
        .andExpect(jsonPath("$[0].cities[0]").value("Tashkent"))
        .andExpect(jsonPath("$[1].code").value("UKR"))
        .andExpect(jsonPath("$[1].timeZone").value("Europe/Kyiv"))
        .andExpect(content().string(containsString("\"vatRate\":8.00")));
  }
}
