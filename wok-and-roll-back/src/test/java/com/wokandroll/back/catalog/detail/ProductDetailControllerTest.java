package com.wokandroll.back.catalog.detail;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import com.wokandroll.back.catalog.detail.dto.ProductDetailResponse;
import com.wokandroll.back.common.error.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(ProductDetailController.class)
class ProductDetailControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ProductDetailService service;

  @Test
  void returnsTheShapeFromTheContract() throws Exception {
    when(service.getProduct(5, "UZB"))
        .thenReturn(
            new ProductDetailResponse(
                5,
                2,
                "Firecracker udon",
                "Udon noodles, chicken, chili, sesame.",
                "/images/udon.png",
                null,
                350,
                null,
                new BigDecimal("48.00"),
                "UZS",
                true,
                true,
                List.of(new ModifierResponse(2, "SIZE", "Large", new BigDecimal("12.00")))));

    mvc.perform(get("/api/v1/products/5").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.pieces").value(nullValue()))
        .andExpect(jsonPath("$.weightGrams").value(350))
        .andExpect(jsonPath("$.volumeMl").value(nullValue()))
        .andExpect(jsonPath("$.price").value(48.00))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.hasModifiers").value(true))
        .andExpect(jsonPath("$.modifiers[0].type").value("SIZE"))
        .andExpect(jsonPath("$.modifiers[0].priceDelta").value(12.00));
  }

  @Test
  void unknownCountryIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/products/5").param("country", "USA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.country").value("must be one of UZB, KAZ, GEO, UKR, CHN"));
    verifyNoInteractions(service);
  }

  @Test
  void missingCountryIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/products/5"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.country").exists());
  }

  @Test
  void idThatIsNotANumberIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/products/abc").param("country", "UZB"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.id").exists());
  }

  @Test
  void notSoldDishIsNotFound() throws Exception {
    when(service.getProduct(anyLong(), anyString()))
        .thenThrow(new NotFoundException("Product 99 is not sold in UZB"));

    mvc.perform(get("/api/v1/products/99").param("country", "UZB"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Product 99 is not sold in UZB"));
  }
}
