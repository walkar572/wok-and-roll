package com.wokandroll.back.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
/** Menu endpoints against the real database with the Flyway seed (#33, #113). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class CatalogApiIntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private JdbcClient jdbc;

  @Test
  void categoriesOfUzbekistan() throws Exception {
    mvc.perform(get("/api/v1/categories").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Sushi & rolls"))
        .andExpect(jsonPath("$[1].name").value("From the wok"))
        .andExpect(jsonPath("$[2].name").value("Sides"));
  }

  @Test
  void productsOfUzbekistanMatchTheSeed() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(10))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].description").value("Salmon, avocado, cucumber."))
        .andExpect(jsonPath("$[0].pieces").value(8))
        .andExpect(jsonPath("$[0].weightGrams").value(250))
        .andExpect(jsonPath("$[0].volumeMl").value(nullValue()))
        .andExpect(jsonPath("$[0].price").value(79000.00))
        .andExpect(jsonPath("$[1].name").value("Tuna nigiri"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[4].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[4].hasModifiers").value(true))
        .andExpect(jsonPath("$[6].name").value("Teriyaki chicken noodles"))
        .andExpect(jsonPath("$[6].hasModifiers").value(false))
        .andExpect(jsonPath("$[6].imageUrl").value("/images/teriyaki-noodles.png"))
        .andExpect(jsonPath("$[9].name").value("Yuzu lemonade"))
        .andExpect(jsonPath("$[9].weightGrams").value(nullValue()))
        .andExpect(jsonPath("$[9].volumeMl").value(400));
  }

  @Test
  void productsOfOneCategoryInChina() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "CHN").param("categoryId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[0].price").value(31.00))
        .andExpect(jsonPath("$[0].currency").value("CNY"));
  }

  @Test
  void theWholeMenuIsSoldInTheUnitedKingdomInPounds() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "GBR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(10))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].price").value(4.95))
        .andExpect(jsonPath("$[0].currency").value("GBP"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[3].price").value(11.75))
        .andExpect(jsonPath("$[4].hasModifiers").value(true))
        .andExpect(jsonPath("$[9].price").value(1.50));
  }

  @Test
  void dishWithoutPriceInTheCountryIsNotReturned() throws Exception {
    jdbc.sql("INSERT INTO product (category_id, name) VALUES (1, 'No price dish')").update();

    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(10))
        .andExpect(jsonPath("$[*].name", not(hasItem("No price dish"))));
  }
}
