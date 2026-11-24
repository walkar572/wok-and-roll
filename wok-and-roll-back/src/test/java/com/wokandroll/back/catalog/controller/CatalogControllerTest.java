package com.wokandroll.back.catalog.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.catalog.dto.CategoryResponse;
import com.wokandroll.back.catalog.dto.ProductResponse;
import com.wokandroll.back.catalog.service.CatalogService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest({CategoryController.class, ProductController.class})
class CatalogControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CatalogService service;

  private static ProductResponse teriyaki() {
    return new ProductResponse(
        7,
        2,
        "Teriyaki chicken noodles",
        "Egg noodles, chicken, teriyaki sauce, vegetables.",
        null,
        null,
        350,
        null,
        new BigDecimal("52.00"),
        "UZS",
        true,
        false);
  }

  @Test
  void categoriesOfACountry() throws Exception {
    when(service.getCategories("UZB")).thenReturn(List.of(new CategoryResponse(3, "Sides")));

    mvc.perform(get("/api/v1/categories").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(3))
        .andExpect(jsonPath("$[0].name").value("Sides"));
  }

  @Test
  void productsHaveTheShapeFromTheContract() throws Exception {
    when(service.getProducts("UZB", null)).thenReturn(List.of(teriyaki()));

    mvc.perform(get("/api/v1/products").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].categoryId").value(2))
        .andExpect(jsonPath("$[0].name").value("Teriyaki chicken noodles"))
        .andExpect(jsonPath("$[0].price").value(52.00))
        .andExpect(jsonPath("$[0].currency").value("UZS"))
        .andExpect(jsonPath("$[0].inStock").value(true))
        .andExpect(jsonPath("$[0].hasModifiers").value(false))
        .andExpect(content().string(containsString("\"imageUrl\":null")))
        .andExpect(content().string(containsString("\"price\":52.00")));
  }

  @Test
  void categoryIdIsPassedToTheService() throws Exception {
    when(service.getProducts("CHN", 2L)).thenReturn(List.of());

    mvc.perform(get("/api/v1/products").param("country", "CHN").param("categoryId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    verify(service).getProducts("CHN", 2L);
  }

  @Test
  void unknownCountryIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "USA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.fields.country").value("must be one of UZB, KAZ, GEO, UKR, CHN, GBR"));
    verifyNoInteractions(service);
  }

  @Test
  void unknownCountryForCategoriesIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/categories").param("country", "uzb"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.country").exists());
    verifyNoInteractions(service);
  }

  @Test
  void missingCountryIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.country").exists());
  }

  @Test
  void categoryIdThatIsNotANumberIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB").param("categoryId", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.categoryId").exists());
  }

  @Test
  void categoryIdBelowOneIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/products").param("country", "UZB").param("categoryId", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.categoryId").value("must be a positive number"));
    verifyNoInteractions(service);
  }
}
