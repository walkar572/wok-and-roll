package com.wokandroll.back.catalog;

import static org.hamcrest.Matchers.contains;
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
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].name").value("Sushi & rolls"))
        .andExpect(jsonPath("$[1].name").value("From the wok"))
        .andExpect(jsonPath("$[2].name").value("Sides"))
        .andExpect(jsonPath("$[3].name").value("Desserts"))
        .andExpect(jsonPath("$[4].name").value("Drinks"));
  }

  @Test
  void productsOfUzbekistanMatchTheSeed() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(24))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].description").value("Salmon, avocado, cucumber."))
        .andExpect(jsonPath("$[0].pieces").value(8))
        .andExpect(jsonPath("$[0].weightGrams").value(250))
        .andExpect(jsonPath("$[0].volumeMl").value(nullValue()))
        .andExpect(jsonPath("$[0].price").value(79000.00))
        .andExpect(jsonPath("$[1].name").value("Tuna nigiri"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[4].name").value("Philadelphia roll"))
        .andExpect(jsonPath("$[4].price").value(85000.00))
        .andExpect(jsonPath("$[7].name").value("Salmon sashimi"))
        .andExpect(jsonPath("$[7].pieces").value(6))
        .andExpect(jsonPath("$[8].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[8].hasModifiers").value(true))
        .andExpect(jsonPath("$[10].name").value("Teriyaki chicken noodles"))
        .andExpect(jsonPath("$[10].hasModifiers").value(false))
        .andExpect(jsonPath("$[10].imageUrl").value("/images/teriyaki-noodles.png"))
        .andExpect(jsonPath("$[11].name").value("Chicken ramen"))
        .andExpect(jsonPath("$[11].hasModifiers").value(true))
        .andExpect(jsonPath("$[17].name").value("Chili garlic peanuts"))
        .andExpect(jsonPath("$[17].weightGrams").value(120))
        .andExpect(jsonPath("$[18].name").value("Mochi ice cream"))
        .andExpect(jsonPath("$[18].pieces").value(3))
        .andExpect(jsonPath("$[21].name").value("Yuzu lemonade"))
        .andExpect(jsonPath("$[21].weightGrams").value(nullValue()))
        .andExpect(jsonPath("$[21].volumeMl").value(400))
        .andExpect(jsonPath("$[23].name").value("Jasmine green tea"))
        .andExpect(jsonPath("$[23].volumeMl").value(500));
  }

  // v1.8: edamame left the menu for the peanuts; the old orders keep its name.
  @Test
  void edamameIsNoLongerOnTheMenu() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", not(hasItem("Edamame"))))
        .andExpect(jsonPath("$[*].name", hasItem("Chili garlic peanuts")));
  }

  @Test
  void theNewCategoriesHaveTheirDishes() throws Exception {
    long desserts = categoryId("Desserts");
    long drinks = categoryId("Drinks");

    mvc.perform(get("/api/v1/products").param("country", "GEO").param("categoryId", "" + desserts))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].name", contains("Mochi ice cream", "Matcha cheesecake", "Mango sticky rice")))
        .andExpect(jsonPath("$[1].price").value(8.90))
        .andExpect(jsonPath("$[1].currency").value("GEL"));
    mvc.perform(get("/api/v1/products").param("country", "UKR").param("categoryId", "" + drinks))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].name", contains("Yuzu lemonade", "Matcha latte", "Jasmine green tea")));
  }

  private long categoryId(String name) {
    return jdbc.sql("SELECT id FROM category WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  @Test
  void productsOfOneCategoryInChina() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "CHN").param("categoryId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[0].price").value(31.00))
        .andExpect(jsonPath("$[0].currency").value("CNY"));
  }

  @Test
  void theWholeMenuIsSoldInTheUnitedKingdomInPounds() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "GBR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(24))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].price").value(4.95))
        .andExpect(jsonPath("$[0].currency").value("GBP"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[3].price").value(11.75))
        .andExpect(jsonPath("$[8].hasModifiers").value(true))
        .andExpect(jsonPath("$[11].price").value(4.05))
        .andExpect(jsonPath("$[21].price").value(1.50));
  }

  @Test
  void dishWithoutPriceInTheCountryIsNotReturned() throws Exception {
    jdbc.sql("INSERT INTO product (category_id, name) VALUES (1, 'No price dish')").update();

    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(24))
        .andExpect(jsonPath("$[*].name", not(hasItem("No price dish"))));
  }
}
