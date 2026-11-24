package com.wokandroll.back.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify. Each test is rolled back.
/**
 * Cart quote against the real database with the Flyway seed (#33, #113). The unit tests use a fake
 * repository; this test checks the SQL of the pricing package and the numbers of the seed. Ids are
 * looked up by name, so the test does not depend on the order of the seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class CartQuoteApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Test
  void twoLargeFirecrackerUdonsWithExtraChickenInUzbekistan() throws Exception {
    // udon 55,000 + Large 15,000 + Extra chicken 12,000 = 82,000 each, x2 = 164,000; VAT 15%
    // = 24,600 -> 188,600, from 100,000 the discount is 3 % = 5,658 -> total 182,942
    long udon = productId("Firecracker udon");
    long large = modifierId("Firecracker udon", "Large");
    long chicken = modifierId("Firecracker udon", "Extra chicken");

    quote(
            """
            {"country": "UZB", "items": [
              {"productId": %d, "quantity": 2, "modifierIds": [%d, %d]}
            ]}
            """
                .formatted(udon, large, chicken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UZB"))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.lines[0].name").value("Firecracker udon"))
        .andExpect(jsonPath("$.lines[0].quantity").value(2))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(82000.00))
        .andExpect(jsonPath("$.lines[0].lineTotal").value(164000.00))
        .andExpect(jsonPath("$.lines[0].modifiers.length()").value(2))
        .andExpect(jsonPath("$.lines[0].modifiers[0].name").value("Large"))
        .andExpect(jsonPath("$.lines[0].modifiers[0].type").value("SIZE"))
        .andExpect(jsonPath("$.lines[0].modifiers[1].name").value("Extra chicken"))
        .andExpect(jsonPath("$.lines[0].modifiers[1].type").value("EXTRA"))
        .andExpect(jsonPath("$.subtotal").value(164000.00))
        .andExpect(jsonPath("$.vatRate").value(15.00))
        .andExpect(jsonPath("$.vat").value(24600.00))
        .andExpect(jsonPath("$.amountInclVat").value(188600.00))
        .andExpect(jsonPath("$.scenario").value("PICKUP"))
        .andExpect(jsonPath("$.discountRate").value(3.00))
        .andExpect(jsonPath("$.discount").value(5658.00))
        .andExpect(jsonPath("$.deliveryFee").value(0.00))
        .andExpect(jsonPath("$.total").value(182942.00));
  }

  @Test
  void deliveryInUzbekistanWithTheCenterZone() throws Exception {
    // 2 x set 189,000 + 2 x California roll 69,000 + Gyoza 39,000 = 555,000; VAT 15% = 83,250
    // amount incl. VAT 638,250 -> from 500,000 the discount is 5 % = 31,912.50; the delivery fee
    // 20,000 is added last: 626,337.50
    quote(
            """
            {"country": "UZB", "scenario": "DELIVERY", "deliveryZoneId": %d, "items": [
              {"productId": %d, "quantity": 2},
              {"productId": %d, "quantity": 2},
              {"productId": %d, "quantity": 1}
            ]}
            """
                .formatted(
                    uzbZoneWithFee("20000.00"),
                    productId("Wok & Roll set for two"),
                    productId("California roll"),
                    productId("Gyoza")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenario").value("DELIVERY"))
        .andExpect(jsonPath("$.subtotal").value(555000.00))
        .andExpect(jsonPath("$.vat").value(83250.00))
        .andExpect(jsonPath("$.amountInclVat").value(638250.00))
        .andExpect(jsonPath("$.discountRate").value(5.00))
        .andExpect(jsonPath("$.discount").value(31912.50))
        .andExpect(jsonPath("$.deliveryFee").value(20000.00))
        .andExpect(jsonPath("$.total").value(626337.50));
  }

  @Test
  void deliveryInTheUnitedKingdomInPounds() throws Exception {
    // 2 x Salmon avocado roll 4.95 = 9.90; udon 3.45 + Large 0.95 + Extra chicken 0.75 = 5.15;
    // subtotal 15.05, VAT 20 % = 3.01 -> 18.06; from 6.00 the discount is 3 % = 0.5418 -> 0.54;
    // London: Westminster 1.25 is added last: 18.77
    long westminster =
        jdbc.sql("SELECT id FROM delivery_zone WHERE name = 'London: Westminster'")
            .query(Long.class)
            .single();
    quote(
            """
            {"country": "GBR", "scenario": "DELIVERY", "deliveryZoneId": %d, "items": [
              {"productId": %d, "quantity": 2},
              {"productId": %d, "quantity": 1, "modifierIds": [%d, %d]}
            ]}
            """
                .formatted(
                    westminster,
                    productId("Salmon avocado roll"),
                    productId("Firecracker udon"),
                    modifierId("Firecracker udon", "Large"),
                    modifierId("Firecracker udon", "Extra chicken")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("GBR"))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.lines[1].unitPrice").value(5.15))
        .andExpect(jsonPath("$.subtotal").value(15.05))
        .andExpect(jsonPath("$.vatRate").value(20.00))
        .andExpect(jsonPath("$.vat").value(3.01))
        .andExpect(jsonPath("$.amountInclVat").value(18.06))
        .andExpect(jsonPath("$.discountRate").value(3.00))
        .andExpect(jsonPath("$.discount").value(0.54))
        .andExpect(jsonPath("$.deliveryFee").value(1.25))
        .andExpect(jsonPath("$.total").value(18.77));
  }

  @Test
  void zoneOfAnotherCountryIsRejected() throws Exception {
    long kazZone =
        jdbc.sql(
                "SELECT z.id FROM delivery_zone z JOIN country c ON c.id = z.country_id "
                    + "WHERE c.code = 'KAZ' AND z.active ORDER BY z.id LIMIT 1")
            .query(Long.class)
            .single();

    quote(
            """
            {"country": "UZB", "scenario": "DELIVERY", "deliveryZoneId": %d, "items": [
              {"productId": %d, "quantity": 1}
            ]}
            """
                .formatted(kazZone, productId("Gyoza")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_DELIVERY_ZONE"))
        .andExpect(jsonPath("$.fields.deliveryZoneId").exists());
  }

  @Test
  void outOfStockTunaNigiriIsRejected() throws Exception {
    quote(
            """
            {"country": "UZB", "items": [{"productId": %d, "quantity": 1}]}
            """
                .formatted(productId("Tuna nigiri")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.fields['items[0].productId']").value("Tuna nigiri is out of stock"));
  }

  @Test
  void udonWithoutASizeIsRejected() throws Exception {
    // the dish has sizes (Regular, Large), so exactly one SIZE is needed; the key is the list
    quote(
            """
            {"country": "UZB", "items": [{"productId": %d, "quantity": 1}]}
            """
                .formatted(productId("Firecracker udon")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODIFIER"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.fields['items[0].modifierIds']").value("choose exactly one size"));
  }

  @Test
  void extraTofuIsNotSoldInChina() throws Exception {
    // Extra tofu has no price in CHN in the seed; the key is the position of the modifier
    quote(
            """
            {"country": "CHN", "items": [
              {"productId": %d, "quantity": 1, "modifierIds": [%d]}
            ]}
            """
                .formatted(
                    productId("Miso mushroom rice"),
                    modifierId("Miso mushroom rice", "Extra tofu")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODIFIER"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.fields['items[0].modifierIds[0]']").exists());
  }

  @Test
  void unknownProductIsRejected() throws Exception {
    quote(
            """
            {"country": "UZB", "items": [{"productId": 999999, "quantity": 1}]}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.fields['items[0].productId']").exists());
  }

  private ResultActions quote(String json) throws Exception {
    return mvc.perform(
        post("/api/v1/cart/quote").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private long uzbZoneWithFee(String fee) {
    return jdbc.sql(
            "SELECT z.id FROM delivery_zone z JOIN country c ON c.id = z.country_id "
                + "WHERE c.code = 'UZB' AND z.active AND z.fee = :fee ORDER BY z.id LIMIT 1")
        .param("fee", new java.math.BigDecimal(fee))
        .query(Long.class)
        .single();
  }

  private long productId(String name) {
    return jdbc.sql("SELECT id FROM product WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  private long modifierId(String productName, String modifierName) {
    return jdbc.sql(
            "SELECT m.id FROM modifier m JOIN product p ON p.id = m.product_id "
                + "WHERE p.name = :product AND m.name = :modifier")
        .param("product", productName)
        .param("modifier", modifierName)
        .query(Long.class)
        .single();
  }
}
