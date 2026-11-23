package com.wokandroll.back.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
        .andExpect(jsonPath("$[0].price").value(62.00))
        .andExpect(jsonPath("$[1].name").value("Tuna nigiri"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[4].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[4].hasModifiers").value(true))
        .andExpect(jsonPath("$[6].name").value("Teriyaki chicken noodles"))
        .andExpect(jsonPath("$[6].hasModifiers").value(false));
  }

  @Test
  void productsOfOneCategoryInChina() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "CHN").param("categoryId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[0].price").value(54.00))
        .andExpect(jsonPath("$[0].currency").value("CNY"));
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
