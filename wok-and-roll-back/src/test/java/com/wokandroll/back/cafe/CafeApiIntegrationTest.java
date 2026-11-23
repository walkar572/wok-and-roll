package com.wokandroll.back.cafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.cafe.repository.CafeRepository;
import com.wokandroll.back.cafe.repository.CafeRepository.CafeRow;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify. Each test is rolled back.
/** GET /api/v1/cafes and findActive against the Flyway seed of #143 (#146). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class CafeApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Autowired private CafeRepository repository;

  @ParameterizedTest
  @CsvSource({
    "UZB, Asia/Tashkent",
    "KAZ, Asia/Almaty",
    "GEO, Asia/Tbilisi",
    "UKR, Europe/Kyiv",
    "CHN, Asia/Shanghai"
  })
  void everyCountryHasTwoCafesInItsTimeZone(String country, String timeZone) throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", country))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].country", everyItem(is(country))))
        .andExpect(jsonPath("$.items[*].timeZone", everyItem(is(timeZone))))
        .andExpect(jsonPath("$.items[*].opensAt", everyItem(is("10:00"))))
        .andExpect(jsonPath("$.items[*].closesAt", everyItem(is("22:00"))));
  }

  @Test
  void cafesOfUzbekistanAreOrderedByName() throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Wok & Roll Chilanzar"))
        .andExpect(jsonPath("$.items[0].address").value("12 Bunyodkor Ave, Tashkent"))
        .andExpect(jsonPath("$.items[0].phone").value("+998712000001"))
        .andExpect(jsonPath("$.items[1].name").value("Wok & Roll Yunusabad"));
  }

  @Test
  void inactiveCafeIsNotOffered() throws Exception {
    long yunusabad = cafeId("Wok & Roll Yunusabad");
    jdbc.sql("UPDATE cafe SET active = FALSE WHERE id = :id").param("id", yunusabad).update();

    mvc.perform(get("/api/v1/cafes").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[*].name", not(hasItem("Wok & Roll Yunusabad"))));
    assertThat(repository.findActive(yunusabad)).isEmpty();
  }

  @Test
  void findActiveGivesHoursCapacityAndTimeZone() {
    CafeRow medeu = repository.findActive(cafeId("Wok & Roll Medeu")).orElseThrow();

    assertThat(medeu.countryCode()).isEqualTo("KAZ");
    assertThat(medeu.opensAt()).isEqualTo(LocalTime.of(10, 0));
    assertThat(medeu.closesAt()).isEqualTo(LocalTime.of(22, 0));
    assertThat(medeu.slotCapacity()).isEqualTo(5);
    assertThat(medeu.timeZone()).isEqualTo("Asia/Almaty");
    assertThat(repository.findActive(999_999)).isEmpty();
  }

  private long cafeId(String name) {
    return jdbc.sql("SELECT id FROM cafe WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
  }
}
