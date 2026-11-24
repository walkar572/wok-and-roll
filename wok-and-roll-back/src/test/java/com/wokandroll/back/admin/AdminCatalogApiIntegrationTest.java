package com.wokandroll.back.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Runs only when CI=true (PostgreSQL on localhost:5432). Each test is rolled back.
/** The admin catalog API (#178, api-contract.md section 3.15) on the seed data. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class AdminCatalogApiIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;

  private String bearer(String role) {
    return "Bearer "
        + jwt.createToken(new AuthUser(1, role.toLowerCase() + "@wokandroll.test", role));
  }

  @Test
  void onlyAdminsUseTheCatalogApi() throws Exception {
    mvc.perform(get("/api/v1/admin/products?country=UZB")).andExpect(status().isUnauthorized());
    for (String role : new String[] {"CUSTOMER", "STAFF", "VIEWER"}) {
      mvc.perform(get("/api/v1/admin/products?country=UZB").header("Authorization", bearer(role)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void theListHasEveryProductWithThePriceOfTheCountry() throws Exception {
    mvc.perform(
            get("/api/v1/admin/products?country=KAZ&pageSize=100")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(10)))
        .andExpect(jsonPath("$.items[0].name").value("Salmon avocado roll"))
        .andExpect(jsonPath("$.items[0].pieces").value(8))
        .andExpect(jsonPath("$.items[0].weightGrams").value(250))
        .andExpect(jsonPath("$.items[0].price").value(3190.00))
        .andExpect(jsonPath("$.items[0].currency").value("KZT"))
        .andExpect(jsonPath("$.items[0].inStock").value(true))
        .andExpect(jsonPath("$.items[0].priceVersion").value(0))
        .andExpect(jsonPath("$.items[1].name").value("Tuna nigiri"))
        .andExpect(jsonPath("$.items[1].inStock").value(false))
        .andExpect(jsonPath("$.nextPageToken").value(nullValue()));
  }

  @Test
  void aNewProductCanBePricedShownAndHidden() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/admin/products?country=GEO")
                    .header("Authorization", bearer("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"categoryId": 2, "name": "Spicy ramen", "description": "Pork, egg.",
                         "imageUrl": "/images/udon.png", "weightGrams": 400}
                        """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.visible").value(true))
            .andExpect(jsonPath("$.pieces").value(nullValue()))
            .andExpect(jsonPath("$.weightGrams").value(400))
            .andExpect(jsonPath("$.price").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int id = JsonPath.read(created, "$.id");

    // not sold in GEO yet: the public menu does not show it
    mvc.perform(get("/api/v1/products/" + id + "?country=GEO")).andExpect(status().isNotFound());

    mvc.perform(
            put("/api/v1/admin/products/" + id + "/prices/GEO")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\": 39.90, \"inStock\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(39.90))
        .andExpect(jsonPath("$.currency").value("GEL"))
        .andExpect(jsonPath("$.version").value(0));

    mvc.perform(get("/api/v1/products/" + id + "?country=GEO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(39.90));

    mvc.perform(
            patch("/api/v1/admin/products/" + id)
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\": false, \"weightGrams\": 0, \"pieces\": 6, \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visible").value(false))
        .andExpect(jsonPath("$.weightGrams").value(nullValue()))
        .andExpect(jsonPath("$.pieces").value(6))
        .andExpect(jsonPath("$.name").value("Spicy ramen"))
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(get("/api/v1/products/" + id + "?country=GEO")).andExpect(status().isNotFound());
  }

  @Test
  void anOldVersionIsAConflict() throws Exception {
    String body = "{\"price\": 50.00, \"inStock\": false, \"version\": 0}";
    mvc.perform(
            put("/api/v1/admin/products/1/prices/UZB")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.inStock").value(false));

    // the second admin still has version 0
    mvc.perform(
            put("/api/v1/admin/products/1/prices/UZB")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
  }

  @Test
  void pricesAndImagesAreChecked() throws Exception {
    mvc.perform(
            put("/api/v1/admin/products/1/prices/UZB")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\": 10.555, \"inStock\": true, \"version\": 0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.price").exists());
    mvc.perform(
            post("/api/v1/admin/products")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"categoryId": 999, "name": "", "imageUrl": "http://x/y.png",
                     "pieces": 0, "weightGrams": 10000}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.categoryId").exists())
        .andExpect(jsonPath("$.fields.name").exists())
        .andExpect(jsonPath("$.fields.imageUrl").exists())
        .andExpect(jsonPath("$.fields.pieces").value("must be from 1 to 999"))
        .andExpect(jsonPath("$.fields.weightGrams").value("must be from 1 to 9999"));
    mvc.perform(
            patch("/api/v1/admin/products/9999")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void categoriesCanBeCreatedAndRenamed() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/admin/categories")
                    .header("Authorization", bearer("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Soups\", \"sortOrder\": 4}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    int id = JsonPath.read(created, "$.id");

    mvc.perform(
            patch("/api/v1/admin/categories/" + id)
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Hot soups\", \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hot soups"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            post("/api/v1/admin/categories")
                .header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"sides\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.name").exists());
  }
}
