package com.wokandroll.back.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].name").value("Sushi & rolls"))
        .andExpect(jsonPath("$[1].name").value("Sets"))
        .andExpect(jsonPath("$[2].name").value("From the wok"))
        .andExpect(jsonPath("$[3].name").value("Sides"))
        .andExpect(jsonPath("$[4].name").value("Desserts"))
        .andExpect(jsonPath("$[5].name").value("Drinks"));
  }

  @Test
  void everyDishSaysWhetherItIsVegetarianAndSpicy() throws Exception {
    String body =
        mvc.perform(get("/api/v1/products").param("country", "UZB"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> products = JsonPath.read(body, "$[*]");
    Map<String, String> marks = new HashMap<>();
    for (Map<String, Object> product : products) {
      marks.put(
          (String) product.get("name"),
          (Boolean.TRUE.equals(product.get("vegetarian")) ? "vegetarian" : "-")
              + (Boolean.TRUE.equals(product.get("spicy")) ? " spicy" : ""));
    }

    assertThat(marks)
        .containsEntry("Miso mushroom rice", "vegetarian")
        .containsEntry("Vegetable spring rolls", "vegetarian spicy")
        .containsEntry("Mochi ice cream", "vegetarian")
        .containsEntry("Salmon avocado roll", "-")
        .containsEntry("California roll", "-")
        .containsEntry("Firecracker udon", "- spicy")
        .containsEntry("Crunchy shrimp roll", "- spicy")
        // a set from its dishes: the Dragon set has the crunchy shrimp roll
        .containsEntry("Dragon set", "- spicy")
        .containsEntry("Philadelphia set", "-");

    // the product modal says the same
    long udon =
        jdbc.sql("SELECT id FROM product WHERE name = 'Firecracker udon'")
            .query(Long.class)
            .single();
    mvc.perform(get("/api/v1/products/" + udon).param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vegetarian").value(false))
        .andExpect(jsonPath("$.spicy").value(true));
  }

  @Test
  void productsOfUzbekistanMatchTheSeed() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].description").value("Salmon, avocado, cucumber."))
        .andExpect(jsonPath("$[0].pieces").value(8))
        .andExpect(jsonPath("$[0].weightGrams").value(250))
        .andExpect(jsonPath("$[0].volumeMl").value(nullValue()))
        .andExpect(jsonPath("$[0].price").value(79000.00))
        .andExpect(jsonPath("$[0].regularPrice").value(nullValue()))
        .andExpect(jsonPath("$[0].setItems.length()").value(0))
        .andExpect(jsonPath("$[1].name").value("Tuna nigiri"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[3].name").value("Philadelphia roll"))
        .andExpect(jsonPath("$[3].price").value(85000.00))
        .andExpect(jsonPath("$[6].name").value("Salmon sashimi"))
        .andExpect(jsonPath("$[6].pieces").value(6))
        .andExpect(jsonPath("$[7].name").value("Wok & Roll set for two"))
        .andExpect(jsonPath("$[13].name").value("Lunch combo"))
        .andExpect(jsonPath("$[14].name").value("Firecracker udon"))
        .andExpect(jsonPath("$[14].hasModifiers").value(true))
        .andExpect(jsonPath("$[16].name").value("Teriyaki chicken noodles"))
        .andExpect(jsonPath("$[16].hasModifiers").value(false))
        .andExpect(jsonPath("$[16].imageUrl").value("/images/teriyaki-noodles.png"))
        .andExpect(jsonPath("$[17].name").value("Chicken ramen"))
        .andExpect(jsonPath("$[17].hasModifiers").value(true))
        .andExpect(jsonPath("$[23].name").value("Chili garlic peanuts"))
        .andExpect(jsonPath("$[23].weightGrams").value(120))
        .andExpect(jsonPath("$[24].name").value("Mochi ice cream"))
        .andExpect(jsonPath("$[24].pieces").value(3))
        .andExpect(jsonPath("$[27].name").value("Yuzu lemonade"))
        .andExpect(jsonPath("$[27].weightGrams").value(nullValue()))
        .andExpect(jsonPath("$[27].volumeMl").value(400))
        .andExpect(jsonPath("$[29].name").value("Jasmine green tea"))
        .andExpect(jsonPath("$[29].volumeMl").value(500));
  }

  // v1.10: a set has its dishes and their sum in the country next to its own, lower price.
  @Test
  void aSetShowsItsDishesAndWhatThemCostOneByOne() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[8].name").value("Philadelphia set"))
        .andExpect(jsonPath("$[8].price").value(199000.00))
        .andExpect(jsonPath("$[8].regularPrice").value(239000.00))
        .andExpect(jsonPath("$[8].pieces").value(24))
        .andExpect(jsonPath("$[8].setItems.length()").value(2))
        .andExpect(jsonPath("$[8].setItems[0].productId").value(productId("Philadelphia roll")))
        .andExpect(jsonPath("$[8].setItems[0].name").value("Philadelphia roll"))
        .andExpect(jsonPath("$[8].setItems[0].quantity").value(2))
        .andExpect(jsonPath("$[8].setItems[1].name").value("California roll"))
        .andExpect(jsonPath("$[8].setItems[1].quantity").value(1))
        .andExpect(jsonPath("$[7].name").value("Wok & Roll set for two"))
        .andExpect(jsonPath("$[7].price").value(189000.00))
        .andExpect(jsonPath("$[7].regularPrice").value(223000.00))
        .andExpect(jsonPath("$[7].setItems[3].name").value("Jasmine green tea"))
        .andExpect(jsonPath("$[7].setItems[3].quantity").value(2));
  }

  @Test
  void theSetsCategoryHasSevenSets() throws Exception {
    mvc.perform(
            get("/api/v1/products")
                .param("country", "KAZ")
                .param("categoryId", "" + categoryId("Sets")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].name",
                contains(
                    "Wok & Roll set for two",
                    "Philadelphia set",
                    "Salmon set",
                    "Dragon set",
                    "Classic set",
                    "Party set",
                    "Lunch combo")))
        .andExpect(jsonPath("$[*].inStock", everyItem(is(true))));
  }

  // Every set is cheaper than its dishes one by one, in every currency.
  @ParameterizedTest
  @ValueSource(strings = {"UZB", "KAZ", "GEO", "UKR", "CHN", "GBR", "USA"})
  void everySetIsCheaperThanItsDishes(String country) throws Exception {
    String body =
        mvc.perform(get("/api/v1/products").param("country", country))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> sets = JsonPath.read(body, "$[?(@.setItems.length() > 0)]");
    assertThat(sets).hasSize(7);
    for (Map<String, Object> set : sets) {
      BigDecimal price = new BigDecimal(set.get("price").toString());
      BigDecimal regular = new BigDecimal(set.get("regularPrice").toString());
      assertThat(price).as("%s in %s", set.get("name"), country).isLessThan(regular);
    }
  }

  @Test
  void theModalOfASetHasItsDishesToo() throws Exception {
    mvc.perform(get("/api/v1/products/" + productId("Party set")).param("country", "USA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(40.95))
        .andExpect(jsonPath("$.regularPrice").value(50.65))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.setItems.length()").value(5))
        .andExpect(jsonPath("$.setItems[3].name").value("Dragon roll"))
        .andExpect(jsonPath("$.modifiers.length()").value(0));
    mvc.perform(get("/api/v1/products/" + productId("Gyoza")).param("country", "USA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.regularPrice").value(nullValue()))
        .andExpect(jsonPath("$.setItems.length()").value(0));
  }

  @Test
  void aSetWithADishNotSoldInTheCountryHasNoRegularPrice() throws Exception {
    jdbc.sql(
            """
            DELETE FROM regional_price
            WHERE product_id = (SELECT id FROM product WHERE name = 'Gyoza')
              AND country_id = (SELECT id FROM country WHERE code = 'GEO')
            """)
        .update();

    mvc.perform(get("/api/v1/products").param("country", "GEO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[13].name").value("Lunch combo"))
        .andExpect(jsonPath("$[13].regularPrice").value(nullValue()))
        .andExpect(jsonPath("$[13].setItems.length()").value(3))
        .andExpect(jsonPath("$[8].regularPrice").value(51.90));
  }

  private long productId(String name) {
    return jdbc.sql("SELECT id FROM product WHERE name = :name")
        .param("name", name)
        .query(Long.class)
        .single();
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
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].price").value(4.95))
        .andExpect(jsonPath("$[0].currency").value("GBP"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[7].price").value(11.75))
        .andExpect(jsonPath("$[7].regularPrice").value(13.95))
        .andExpect(jsonPath("$[14].hasModifiers").value(true))
        .andExpect(jsonPath("$[17].price").value(4.05))
        .andExpect(jsonPath("$[27].price").value(1.50));
  }

  @Test
  void theWholeMenuIsSoldInTheUnitedStatesInDollars() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "USA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$[0].price").value(6.20))
        .andExpect(jsonPath("$[0].currency").value("USD"))
        .andExpect(jsonPath("$[1].inStock").value(false))
        .andExpect(jsonPath("$[3].price").value(6.70))
        .andExpect(jsonPath("$[8].price").value(15.75))
        .andExpect(jsonPath("$[8].regularPrice").value(18.85))
        .andExpect(jsonPath("$[14].hasModifiers").value(true))
        .andExpect(jsonPath("$[17].hasModifiers").value(true))
        .andExpect(jsonPath("$[29].price").value(1.40));
  }

  @Test
  void dishWithoutPriceInTheCountryIsNotReturned() throws Exception {
    jdbc.sql("INSERT INTO product (category_id, name) VALUES (1, 'No price dish')").update();

    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[*].name", not(hasItem("No price dish"))));
  }
}
