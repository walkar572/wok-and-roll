package com.wokandroll.back.catalog.detail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true: GitHub Actions sets it and has PostgreSQL 16 on localhost:5432.
// Locally: start the database, then run CI=true mvn verify. Each test is rolled back.
/** Product modal against the real database with the Flyway seed (#33, #113). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class ProductDetailApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Test
  void udonInUzbekistanHasTheModifiersFromTheContract() throws Exception {
    mvc.perform(get("/api/v1/products/5").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Firecracker udon"))
        .andExpect(jsonPath("$.price").value(48.00))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.hasModifiers").value(true))
        .andExpect(jsonPath("$.modifiers.length()").value(4))
        .andExpect(jsonPath("$.modifiers[0].id").value(1))
        .andExpect(jsonPath("$.modifiers[0].type").value("SIZE"))
        .andExpect(jsonPath("$.modifiers[0].priceDelta").value(0.00))
        .andExpect(jsonPath("$.modifiers[1].name").value("Large"))
        .andExpect(jsonPath("$.modifiers[1].priceDelta").value(12.00))
        .andExpect(jsonPath("$.modifiers[2].type").value("EXTRA"))
        .andExpect(jsonPath("$.modifiers[2].name").value("Extra chicken"))
        .andExpect(jsonPath("$.modifiers[3].name").value("Fried egg"));
  }

  @Test
  void modifierWithoutPriceInTheCountryIsNotReturned() throws Exception {
    // Extra tofu (Miso mushroom rice) has no price in China in the seed
    mvc.perform(get("/api/v1/products/6").param("country", "CHN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modifiers.length()").value(2))
        .andExpect(jsonPath("$.modifiers[1].priceDelta").value(11.50));
  }

  @Test
  void dishWithoutModifiers() throws Exception {
    mvc.perform(get("/api/v1/products/7").param("country", "KAZ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasModifiers").value(false))
        .andExpect(jsonPath("$.modifiers.length()").value(0));
  }

  @Test
  void dishInAHiddenCategoryIsNotFound() throws Exception {
    jdbc.sql("UPDATE category SET visible = FALSE WHERE name = 'From the wok'").update();

    mvc.perform(get("/api/v1/products/5").param("country", "UZB"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void hiddenDishIsNotFound() throws Exception {
    jdbc.sql("UPDATE product SET visible = FALSE WHERE id = 5").update();

    mvc.perform(get("/api/v1/products/5").param("country", "UZB")).andExpect(status().isNotFound());
  }

  @Test
  void hiddenModifierIsNotReturned() throws Exception {
    jdbc.sql("UPDATE modifier SET visible = FALSE WHERE name = 'Fried egg'").update();

    mvc.perform(get("/api/v1/products/5").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modifiers.length()").value(3));
  }

  @Test
  void unknownDishIsNotFound() throws Exception {
    mvc.perform(get("/api/v1/products/99").param("country", "UZB"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product 99 is not sold in UZB"));
  }
}
