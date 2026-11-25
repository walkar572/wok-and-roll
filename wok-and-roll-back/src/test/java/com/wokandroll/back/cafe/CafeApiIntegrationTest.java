package com.wokandroll.back.cafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    "CHN, Asia/Shanghai",
    "GBR, Europe/London",
    "USA, America/New_York"
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

  // v1.9: the city of every café, the one the guest chooses in the interface.
  @ParameterizedTest
  @CsvSource({
    "UZB, Tashkent",
    "KAZ, Almaty",
    "GEO, Tbilisi",
    "UKR, Kyiv",
    "CHN, Shanghai",
    "GBR, London",
    "USA, Boston"
  })
  void everyCafeIsInTheCityOfItsCountry(String country, String city) throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", country))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].city", everyItem(is(city))))
        .andExpect(jsonPath("$.items[*].address", everyItem(endsWith(city))));
  }

  @Test
  void theCafesOfBostonHaveTheirPhonesAndPlaces() throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", "USA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("Wok & Roll Back Bay"))
        .andExpect(jsonPath("$.items[0].address").value("230 Newbury St, Boston"))
        .andExpect(jsonPath("$.items[0].phone").value("+16175550142"))
        .andExpect(jsonPath("$.items[0].latitude").value(42.349702))
        .andExpect(jsonPath("$.items[1].name").value("Wok & Roll Seaport"))
        .andExpect(jsonPath("$.items[1].phone").value("+16175550173"));
  }

  @Test
  void aCafeWithoutACityIsRefused() {
    assertThatThrownBy(
            () ->
                jdbc.sql("UPDATE cafe SET city = ' ' WHERE id = :id")
                    .param("id", cafeId("Wok & Roll Yunusabad"))
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // Every café of the seed is on the map, inside its city (v1.7): latitude and longitude ranges.
  @ParameterizedTest
  @CsvSource({
    "UZB, 41.2, 41.4, 69.1, 69.4",
    "KAZ, 43.1, 43.4, 76.8, 77.1",
    "GEO, 41.6, 41.8, 44.7, 44.9",
    "UKR, 50.3, 50.6, 30.3, 30.7",
    "CHN, 31.1, 31.4, 121.3, 121.7",
    "GBR, 51.4, 51.6, -0.3, 0.1",
    "USA, 42.3, 42.4, -71.2, -71.0"
  })
  void everyCafeIsOnTheMapInItsCity(
      String country, double south, double north, double west, double east) throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", country))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.items[*].latitude", everyItem(both(greaterThan(south)).and(lessThan(north)))))
        .andExpect(
            jsonPath(
                "$.items[*].longitude", everyItem(both(greaterThan(west)).and(lessThan(east)))));
  }

  @Test
  void chilanzarIsAtItsAddress() throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", "UZB"))
        .andExpect(jsonPath("$.items[0].name").value("Wok & Roll Chilanzar"))
        .andExpect(jsonPath("$.items[0].latitude").value(41.272187))
        .andExpect(jsonPath("$.items[0].longitude").value(69.203167));
  }

  @Test
  void aCafeWithoutAPlaceIsStillOffered() throws Exception {
    jdbc.sql("UPDATE cafe SET latitude = NULL, longitude = NULL WHERE id = :id")
        .param("id", cafeId("Wok & Roll Yunusabad"))
        .update();

    mvc.perform(get("/api/v1/cafes").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[1].latitude").value(nullValue()))
        .andExpect(jsonPath("$.items[1].longitude").value(nullValue()));
  }

  @Test
  void halfAPlaceIsRefusedByTheDatabase() {
    long medeu = cafeId("Wok & Roll Medeu");

    assertThatThrownBy(
            () ->
                jdbc.sql("UPDATE cafe SET longitude = NULL WHERE id = :id")
                    .param("id", medeu)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
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
    assertThat(medeu.latitude()).isEqualTo(43.249177);
    assertThat(medeu.longitude()).isEqualTo(76.956143);
    assertThat(repository.findActive(999_999)).isEmpty();
  }

  private long cafeId(String name) {
    return jdbc.sql("SELECT id FROM cafe WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
  }
}
