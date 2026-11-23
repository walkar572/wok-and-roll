package com.wokandroll.back.slot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.slot.dto.TimeSlotResponse;
import com.wokandroll.back.slot.dto.TimeSlotsResponse;
import com.wokandroll.back.slot.service.SlotService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(SecurityConfig.class)
@WebMvcTest(TimeSlotController.class)
class TimeSlotControllerTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

  @Autowired private MockMvc mvc;

  @MockitoBean private SlotService service;

  @Test
  void returnsTheShapeFromTheContractWithoutAToken() throws Exception {
    TimeSlotResponse free = new TimeSlotResponse("10:00", "10:30", 5, 5, true, null);
    TimeSlotResponse full = new TimeSlotResponse("10:30", "11:00", 5, 0, false, "FULL");
    when(service.getSlots(1, DATE))
        .thenReturn(new TimeSlotsResponse(1, DATE, "Asia/Tashkent", List.of(free, full)));

    mvc.perform(get("/api/v1/time-slots").param("cafeId", "1").param("date", "2026-11-20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cafeId").value(1))
        .andExpect(jsonPath("$.date").value("2026-11-20"))
        .andExpect(jsonPath("$.timeZone").value("Asia/Tashkent"))
        .andExpect(jsonPath("$.slots.length()").value(2))
        .andExpect(jsonPath("$.slots[0].start").value("10:00"))
        .andExpect(jsonPath("$.slots[0].end").value("10:30"))
        .andExpect(jsonPath("$.slots[0].capacity").value(5))
        .andExpect(jsonPath("$.slots[0].remaining").value(5))
        .andExpect(jsonPath("$.slots[0].available").value(true))
        .andExpect(jsonPath("$.slots[1].available").value(false))
        .andExpect(jsonPath("$.slots[1].reason").value("FULL"))
        .andExpect(content().string(containsString("\"reason\":null")));
  }

  @Test
  void missingDateIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/time-slots").param("cafeId", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.date").exists());
    verifyNoInteractions(service);
  }

  @Test
  void dateInAnotherFormatIsBadRequest() throws Exception {
    mvc.perform(get("/api/v1/time-slots").param("cafeId", "1").param("date", "20.11.2026"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.date").exists());
    verifyNoInteractions(service);
  }

  @Test
  void cafeIdBelowOneIsValidationError() throws Exception {
    mvc.perform(get("/api/v1/time-slots").param("cafeId", "0").param("date", "2026-11-20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.cafeId").value("must be a positive number"));
    verifyNoInteractions(service);
  }

  @Test
  void unknownCafeFromTheServiceIsShownAsItIs() throws Exception {
    when(service.getSlots(99, DATE))
        .thenThrow(
            new ApiException(
                HttpStatus.BAD_REQUEST,
                "UNKNOWN_CAFE",
                "Unknown café",
                Map.of("cafeId", "Café 99 does not exist or is closed")));

    mvc.perform(get("/api/v1/time-slots").param("cafeId", "99").param("date", "2026-11-20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_CAFE"))
        .andExpect(jsonPath("$.fields.cafeId").exists());
  }
}
