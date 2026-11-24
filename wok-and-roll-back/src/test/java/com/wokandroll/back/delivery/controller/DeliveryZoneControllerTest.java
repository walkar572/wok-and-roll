package com.wokandroll.back.delivery.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.delivery.dto.DeliveryZoneListResponse;
import com.wokandroll.back.delivery.dto.DeliveryZoneResponse;
import com.wokandroll.back.delivery.service.DeliveryZoneService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(DeliveryZoneController.class)
class DeliveryZoneControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private DeliveryZoneService service;

  @Test
  void returnsTheShapeFromTheContractWithoutAToken() throws Exception {
    DeliveryZoneResponse center =
        new DeliveryZoneResponse(
            1, "UZB", "Tashkent: Center", 1, new BigDecimal("20.00"), "UZS", 45);
    when(service.getZones("UZB")).thenReturn(new DeliveryZoneListResponse(List.of(center)));

    mvc.perform(get("/api/v1/delivery-zones").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(1))
        .andExpect(jsonPath("$.items[0].country").value("UZB"))
        .andExpect(jsonPath("$.items[0].name").value("Tashkent: Center"))
        .andExpect(jsonPath("$.items[0].cafeId").value(1))
        .andExpect(jsonPath("$.items[0].fee").value(20.00))
        .andExpect(jsonPath("$.items[0].currency").value("UZS"))
        .andExpect(jsonPath("$.items[0].deliveryMinutes").value(45))
        .andExpect(content().string(containsString("\"fee\":20.00")));
  }

  @Test
  void unknownCountryIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/delivery-zones").param("country", "USA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.fields.country").value("must be one of UZB, KAZ, GEO, UKR, CHN, GBR"));
    verifyNoInteractions(service);
  }

  @Test
  void missingCountryIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/delivery-zones"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.country").exists());
    verifyNoInteractions(service);
  }
}
