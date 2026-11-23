package com.wokandroll.back.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wokandroll.back.common.time.CountryClock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify. Each test is rolled back.
/**
 * {@code POST /api/v1/orders} against the real database with the Flyway seed (#149, #150): the
 * saved rows, the slot places, idempotency, and the error codes. The seed has the café "Wok & Roll
 * Chilanzar" (5 orders per slot) and the zone "Tashkent: Center" (fee 20,000.00) in UZB. A Salmon
 * avocado roll is 79,000.00, so two of them are 158,000.00 + VAT 23,700.00 = 181,700.00, discount 3
 * % = 5,451.00, total 176,249.00 for pickup and 196,249.00 for delivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class OrderApiIntegrationTest {

  private static final String NUMBER = "WR-[0-9A-HJKMNP-TV-Z]{8}";

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Autowired private CountryClock clock;

  @Test
  void pickupOrderIsSavedWithTheServerPrice() throws Exception {
    long before = orderCount();

    place(pickup(tomorrow(), "12:30", "176249.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderNumber").value(matchesPattern(NUMBER)))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.country").value("UZB"))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.orderDate").value(tomorrow()))
        .andExpect(jsonPath("$.slotStart").value("12:30"))
        .andExpect(jsonPath("$.slotEnd").value("13:00"))
        .andExpect(jsonPath("$.scenario").value("PICKUP"))
        .andExpect(jsonPath("$.cafe.name").value("Wok & Roll Chilanzar"))
        .andExpect(jsonPath("$.deliveryZone").value(nullValue()))
        .andExpect(jsonPath("$.deliveryAddress").value(nullValue()))
        .andExpect(jsonPath("$.lines[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$.lines[0].quantity").value(2))
        .andExpect(jsonPath("$.lines[0].lineTotal").value(158000.00))
        .andExpect(jsonPath("$.subtotal").value(158000.00))
        .andExpect(jsonPath("$.vat").value(23700.00))
        .andExpect(jsonPath("$.amountInclVat").value(181700.00))
        .andExpect(jsonPath("$.discountRate").value(3.00))
        .andExpect(jsonPath("$.discount").value(5451.00))
        .andExpect(jsonPath("$.deliveryFee").value(0.00))
        .andExpect(jsonPath("$.total").value(176249.00))
        // a guest: no Location
        .andExpect(header().doesNotExist("Location"))
        .andExpect(header().doesNotExist("Idempotent-Replayed"));

    assertThat(orderCount()).isEqualTo(before + 1);
    assertThat(reserved(cafeId(), tomorrow(), "12:30")).isEqualTo(1);
    // the email is saved in lower case, the line is copied with its name and prices
    assertThat(
            jdbc.sql("SELECT contact_email FROM customer_order ORDER BY id DESC LIMIT 1")
                .query(String.class)
                .single())
        .isEqualTo("aziz@mail.uz");
    // only the lines of this order: the sales history seed (#171) has its own lines
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM order_item"
                        + " WHERE order_id = (SELECT max(id) FROM customer_order)")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void deliveryOrderUsesTheCafeAndTheFeeOfTheZone() throws Exception {
    place(delivery(tomorrow(), "13:00", "196249.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scenario").value("DELIVERY"))
        .andExpect(jsonPath("$.cafe.name").value("Wok & Roll Chilanzar"))
        .andExpect(jsonPath("$.deliveryZone.name").value("Tashkent: Center"))
        .andExpect(jsonPath("$.deliveryAddress").value("Amir Temur 15, apt 4"))
        .andExpect(jsonPath("$.deliveryFee").value(20000.00))
        .andExpect(jsonPath("$.total").value(196249.00));
  }

  @Test
  void aLoggedInUserGetsTheLocationHeaderAndTheOrderIsLinked() throws Exception {
    String email = "order-test-" + UUID.randomUUID() + "@mail.uz";
    String registered =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "password": "secret123", "fullName": "Order Test"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(registered, "$.token");

    String number =
        JsonPath.read(
            place(
                    pickup(tomorrow(), "14:00", "176249.00"),
                    request -> request.header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(
                    header().string("Location", matchesPattern("/api/v1/me/orders/" + NUMBER)))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.orderNumber");

    assertThat(
            jdbc.sql("SELECT user_id FROM customer_order WHERE order_number = :n")
                .param("n", number)
                .query(Long.class)
                .single())
        .isNotNull();
  }

  @Test
  void aBadTokenIsA401NotAGuestOrder() throws Exception {
    long before = orderCount();

    place(
            pickup(tomorrow(), "12:30", "176249.00"),
            request -> request.header("Authorization", "Bearer not-a-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    assertThat(orderCount()).isEqualTo(before);
  }

  @Test
  void theSameKeyAndBodyReplaysTheFirstOrder() throws Exception {
    String key = UUID.randomUUID().toString();
    String body = pickup(tomorrow(), "12:30", "176249.00");
    long before = orderCount();

    String first =
        place(body, request -> request.header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        place(body, request -> request.header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);
    assertThat(orderCount()).isEqualTo(before + 1);
    assertThat(reserved(cafeId(), tomorrow(), "12:30")).isEqualTo(1); // no second place
  }

  @Test
  void theSameKeyWithAnotherBodyIs422() throws Exception {
    String key = UUID.randomUUID().toString();
    place(
            pickup(tomorrow(), "12:30", "176249.00"),
            request -> request.header("Idempotency-Key", key))
        .andExpect(status().isCreated());

    place(
            pickup(tomorrow(), "13:00", "176249.00"),
            request -> request.header("Idempotency-Key", key))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void aKeyThatIsNotAUuidIsA400() throws Exception {
    place(
            pickup(tomorrow(), "12:30", "176249.00"),
            request -> request.header("Idempotency-Key", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields['Idempotency-Key']").exists());
  }

  @Test
  void aWrongExpectedTotalIs409AndNothingIsSaved() throws Exception {
    long before = orderCount();

    place(pickup(tomorrow(), "12:30", "1.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));

    assertThat(orderCount()).isEqualTo(before);
    assertThat(reserved(cafeId(), tomorrow(), "12:30")).isZero();
  }

  @Test
  void aFullSlotIs409() throws Exception {
    jdbc.sql("UPDATE cafe SET slot_capacity = 1 WHERE id = :id").param("id", cafeId()).update();

    place(pickup(tomorrow(), "15:00", "176249.00")).andExpect(status().isCreated());
    place(pickup(tomorrow(), "15:00", "176249.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_FULL"));

    assertThat(reserved(cafeId(), tomorrow(), "15:00")).isEqualTo(1);
  }

  @Test
  void anotherSlotOfTheSameCafeIsStillFree() throws Exception {
    jdbc.sql("UPDATE cafe SET slot_capacity = 1 WHERE id = :id").param("id", cafeId()).update();

    place(pickup(tomorrow(), "15:00", "176249.00")).andExpect(status().isCreated());
    place(pickup(tomorrow(), "15:30", "176249.00")).andExpect(status().isCreated());
  }

  @Test
  void allProblemsAreReturnedTogetherAndTheCodeIsTheFirstOne() throws Exception {
    place(
            """
            {"country": "UZB", "orderDate": "2020-01-01", "scenario": "DELIVERY", "cafeId": 1,
             "deliveryZoneId": 999999, "deliveryAddress": "ab", "slotStart": "12:15x",
             "contactName": "A", "contactPhone": "12345", "contactEmail": "nope",
             "items": [{"productId": %d, "quantity": 0.5}]}
            """
                .formatted(productId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PAST_DATE"))
        .andExpect(jsonPath("$.fields.orderDate").exists())
        .andExpect(jsonPath("$.fields.cafeId").exists())
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists())
        .andExpect(jsonPath("$.fields.deliveryAddress").exists())
        .andExpect(jsonPath("$.fields.slotStart").exists())
        .andExpect(jsonPath("$.fields.contactName").exists())
        .andExpect(jsonPath("$.fields.contactPhone").exists())
        .andExpect(jsonPath("$.fields.contactEmail").exists())
        .andExpect(jsonPath("$.fields['items[0].quantity']").exists());
  }

  @Test
  void unknownCafeUnknownZoneAndWrongSlotsHaveTheirOwnCodes() throws Exception {
    place(
            pickup(tomorrow(), "12:30", "176249.00")
                .replace("\"cafeId\": " + cafeId(), "\"cafeId\": 999999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_CAFE"))
        .andExpect(jsonPath("$.fields.cafeId").exists());

    place(
            delivery(tomorrow(), "12:30", "196249.00")
                .replace("\"deliveryZoneId\": " + zoneId(), "\"deliveryZoneId\": 999999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_DELIVERY_ZONE"))
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists());

    // 12:15 is not on the 30-minute grid, 22:00 is the closing time, 09:30 is before opening
    for (String slot : new String[] {"12:15", "22:00", "09:30"}) {
      place(pickup(tomorrow(), slot, "176249.00"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_SLOT"))
          .andExpect(jsonPath("$.fields.slotStart").exists());
    }
  }

  @Test
  void aDateMoreThanSevenDaysAheadIsAValidationError() throws Exception {
    String farDate = clock.today("UZB").plusDays(8).toString();

    place(pickup(farDate, "12:30", "176249.00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.orderDate").exists());
  }

  // ---- helpers -----------------------------------------------------------------------------

  private ResultActions place(String json) throws Exception {
    return place(json, request -> request);
  }

  private ResultActions place(
      String json, java.util.function.UnaryOperator<MockHttpServletRequestBuilder> headers)
      throws Exception {
    return mvc.perform(
        headers.apply(
            post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(json)));
  }

  private String tomorrow() {
    return clock.today("UZB").plusDays(1).toString();
  }

  private String pickup(String date, String slot, String expectedTotal) {
    return """
    {"country": "UZB", "orderDate": "%s", "scenario": "PICKUP", "cafeId": %d,
     "slotStart": "%s", "contactName": "Aziz Karimov", "contactPhone": "+998901234567",
     "contactEmail": "Aziz@Mail.uz", "expectedTotal": %s,
     "items": [{"productId": %d, "quantity": 2}]}
    """
        .formatted(date, cafeId(), slot, expectedTotal, productId());
  }

  private String delivery(String date, String slot, String expectedTotal) {
    return """
    {"country": "UZB", "orderDate": "%s", "scenario": "DELIVERY", "deliveryZoneId": %d,
     "deliveryAddress": "Amir Temur 15, apt 4", "slotStart": "%s",
     "contactName": "Aziz Karimov", "contactPhone": "+998901234567", "expectedTotal": %s,
     "items": [{"productId": %d, "quantity": 2}]}
    """
        .formatted(date, zoneId(), slot, expectedTotal, productId());
  }

  private long cafeId() {
    return jdbc.sql("SELECT id FROM cafe WHERE name = 'Wok & Roll Chilanzar'")
        .query(Long.class)
        .single();
  }

  private long zoneId() {
    return jdbc.sql("SELECT id FROM delivery_zone WHERE name = 'Tashkent: Center'")
        .query(Long.class)
        .single();
  }

  private long productId() {
    return jdbc.sql("SELECT id FROM product WHERE name = 'Salmon avocado roll'")
        .query(Long.class)
        .single();
  }

  private long orderCount() {
    return jdbc.sql("SELECT count(*) FROM customer_order").query(Long.class).single();
  }

  /** The taken places of a slot; 0 if nothing is reserved yet. */
  private long reserved(long cafeId, String date, String start) {
    return jdbc.sql(
            "SELECT coalesce(sum(reserved), 0) FROM slot_reservation "
                + "WHERE cafe_id = :cafe AND slot_date = CAST(:date AS date) "
                + "AND slot_start = CAST(:start AS time)")
        .param("cafe", cafeId)
        .param("date", date)
        .param("start", start)
        .query(Long.class)
        .single();
  }
}
