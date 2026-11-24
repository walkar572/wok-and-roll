package com.wokandroll.back.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.common.time.CountryClock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify.
/** GET /api/v1/countries against the real database with the Flyway seed (#33, #143). */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class CountryApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private CountryClock countryClock;

  @Test
  void countriesComeFromTheSeedInOrder() throws Exception {
    mvc.perform(get("/api/v1/countries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].code").value("UZB"))
        .andExpect(jsonPath("$[0].name").value("Uzbekistan"))
        .andExpect(jsonPath("$[0].currency").value("UZS"))
        .andExpect(jsonPath("$[0].vatRate").value(15.00))
        .andExpect(jsonPath("$[0].timeZone").value("Asia/Tashkent"))
        .andExpect(jsonPath("$[1].code").value("KAZ"))
        .andExpect(jsonPath("$[1].timeZone").value("Asia/Almaty"))
        .andExpect(jsonPath("$[2].code").value("GEO"))
        .andExpect(jsonPath("$[2].timeZone").value("Asia/Tbilisi"))
        .andExpect(jsonPath("$[3].code").value("UKR"))
        .andExpect(jsonPath("$[3].vatRate").value(8.00))
        .andExpect(jsonPath("$[3].timeZone").value("Europe/Kyiv"))
        .andExpect(jsonPath("$[4].code").value("CHN"))
        .andExpect(jsonPath("$[4].currency").value("CNY"))
        .andExpect(jsonPath("$[4].timeZone").value("Asia/Shanghai"))
        .andExpect(jsonPath("$[5].code").value("GBR"))
        .andExpect(jsonPath("$[5].name").value("United Kingdom"))
        .andExpect(jsonPath("$[5].currency").value("GBP"))
        .andExpect(jsonPath("$[5].vatRate").value(20.00))
        .andExpect(jsonPath("$[5].timeZone").value("Europe/London"));
  }

  @Test
  void countryClockReadsTheTimeZoneFromTheDatabase() {
    assertThat(countryClock.zone("KAZ")).isEqualTo(ZoneId.of("Asia/Almaty"));
    assertThat(countryClock.zone("CHN")).isEqualTo(ZoneId.of("Asia/Shanghai"));
    assertThat(countryClock.zone("GBR")).isEqualTo(ZoneId.of("Europe/London"));
  }
}
