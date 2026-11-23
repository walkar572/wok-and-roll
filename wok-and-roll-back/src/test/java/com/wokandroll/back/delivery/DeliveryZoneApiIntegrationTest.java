package com.wokandroll.back.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
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
/** GET /api/v1/delivery-zones and findActive against the Flyway seed of #143 (#147). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class DeliveryZoneApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Autowired private DeliveryZoneRepository repository;

  /** Near zone, center and far zone of every country, in its own currency (realistic fees). */
  @ParameterizedTest
  @CsvSource({
    "UZB, 15000.00, 20000.00, 30000.00",
    "KAZ, 600.00, 800.00, 1200.00",
    "GEO, 3.50, 4.50, 6.50",
    "UKR, 50.00, 65.00, 100.00",
    "CHN, 8.00, 11.00, 17.00"
  })
  void everyCountryHasThreeZonesWithItsOwnFees(
      String country, String near, String center, String far) throws Exception {
    mvc.perform(get("/api/v1/delivery-zones").param("country", country))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[*].country", everyItem(is(country))))
        .andExpect(content().string(containsString("\"fee\":" + near)))
        .andExpect(content().string(containsString("\"fee\":" + center)))
        .andExpect(content().string(containsString("\"fee\":" + far)));
  }

  @Test
  void zonesOfUzbekistanAreOrderedByName() throws Exception {
    int chilanzar = cafeId("Wok & Roll Chilanzar");
    int yunusabad = cafeId("Wok & Roll Yunusabad");

    mvc.perform(get("/api/v1/delivery-zones").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Tashkent: Center"))
        .andExpect(jsonPath("$.items[0].cafeId").value(chilanzar))
        .andExpect(jsonPath("$.items[0].fee").value(20000.00))
        .andExpect(jsonPath("$.items[0].currency").value("UZS"))
        .andExpect(jsonPath("$.items[0].deliveryMinutes").value(45))
        .andExpect(jsonPath("$.items[1].name").value("Tashkent: Sergeli"))
        .andExpect(jsonPath("$.items[1].cafeId").value(chilanzar))
        .andExpect(jsonPath("$.items[1].fee").value(30000.00))
        .andExpect(jsonPath("$.items[2].name").value("Tashkent: Yunusabad"))
        .andExpect(jsonPath("$.items[2].cafeId").value(yunusabad))
        .andExpect(jsonPath("$.items[2].fee").value(15000.00))
        .andExpect(content().string(containsString("\"fee\":20000.00")));
  }

  @Test
  void findActiveGivesTheZoneOnlyInItsOwnCountry() {
    int center = zoneId("Tashkent: Center");

    DeliveryZoneRow zone = repository.findActive("UZB", center).orElseThrow();

    assertThat(zone.name()).isEqualTo("Tashkent: Center");
    assertThat(zone.fee()).isEqualByComparingTo("20000.00");
    assertThat(zone.currency()).isEqualTo("UZS");
    assertThat(zone.cafeId()).isEqualTo(cafeId("Wok & Roll Chilanzar"));
    assertThat(repository.findActive("KAZ", center)).isEmpty();
    assertThat(repository.findActive("UZB", 999_999)).isEmpty();
  }

  @Test
  void inactiveZoneIsNotOffered() throws Exception {
    int sergeli = zoneId("Tashkent: Sergeli");
    jdbc.sql("UPDATE delivery_zone SET active = FALSE WHERE id = :id")
        .param("id", sergeli)
        .update();

    mvc.perform(get("/api/v1/delivery-zones").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].name", not(hasItem("Tashkent: Sergeli"))));
    assertThat(repository.findActive("UZB", sergeli)).isEmpty();
  }

  @Test
  void zoneOfAnInactiveCafeIsNotOffered() throws Exception {
    jdbc.sql("UPDATE cafe SET active = FALSE WHERE name = 'Wok & Roll Yunusabad'").update();

    mvc.perform(get("/api/v1/delivery-zones").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[*].name", not(hasItem("Tashkent: Yunusabad"))));
  }

  private int zoneId(String name) {
    return jdbc.sql("SELECT id FROM delivery_zone WHERE name = :name")
        .param("name", name)
        .query(Integer.class)
        .single();
  }

  private int cafeId(String name) {
    return jdbc.sql("SELECT id FROM cafe WHERE name = :name")
        .param("name", name)
        .query(Integer.class)
        .single();
  }
}
