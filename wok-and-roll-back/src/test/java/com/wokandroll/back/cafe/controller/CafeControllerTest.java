package com.wokandroll.back.cafe.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.cafe.dto.CafeListResponse;
import com.wokandroll.back.cafe.dto.CafeResponse;
import com.wokandroll.back.cafe.service.CafeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(CafeController.class)
class CafeControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CafeService service;

  @Test
  void returnsTheShapeFromTheContractWithoutAToken() throws Exception {
    CafeResponse chilanzar =
        new CafeResponse(
            1,
            "UZB",
            "Wok & Roll Chilanzar",
            "12 Bunyodkor Ave, Tashkent",
            "+998712000001",
            "10:00",
            "22:00",
            "Asia/Tashkent");
    when(service.getCafes("UZB")).thenReturn(new CafeListResponse(List.of(chilanzar)));

    mvc.perform(get("/api/v1/cafes").param("country", "UZB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(1))
        .andExpect(jsonPath("$.items[0].country").value("UZB"))
        .andExpect(jsonPath("$.items[0].name").value("Wok & Roll Chilanzar"))
        .andExpect(jsonPath("$.items[0].address").value("12 Bunyodkor Ave, Tashkent"))
        .andExpect(jsonPath("$.items[0].phone").value("+998712000001"))
        .andExpect(jsonPath("$.items[0].opensAt").value("10:00"))
        .andExpect(jsonPath("$.items[0].closesAt").value("22:00"))
        .andExpect(jsonPath("$.items[0].timeZone").value("Asia/Tashkent"));
  }

  @Test
  void unknownCountryIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/cafes").param("country", "USA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.fields.country").value("must be one of UZB, KAZ, GEO, UKR, CHN, GBR"));
    verifyNoInteractions(service);
  }

  @Test
  void missingCountryIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/cafes"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.country").exists());
    verifyNoInteractions(service);
  }
}
