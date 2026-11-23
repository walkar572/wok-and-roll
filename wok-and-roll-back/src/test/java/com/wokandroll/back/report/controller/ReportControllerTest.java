package com.wokandroll.back.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.service.ReportService;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

  private static final String URL = "/api/v1/reports/sales-by-region?from=2026-11-01&to=2026-11-30";

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockitoBean private ReportService service;

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "user@wokandroll.test", role));
  }

  private static SalesByRegionResponse example() {
    return new SalesByRegionResponse(
        LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 30),
        Instant.parse("2026-11-20T09:15:00Z"),
        120,
        List.of(
            new RegionSales(
                "UZB",
                "Uzbekistan",
                "UZS",
                120,
                new BigDecimal("52340.00"),
                new BigDecimal("7851.00"),
                new BigDecimal("2410.35"),
                new BigDecimal("1460.00"),
                new BigDecimal("59240.65"),
                new BigDecimal("493.67"))));
  }

  @Test
  void viewerGetsTheReportInTheContractShape() throws Exception {
    when(service.salesByRegion(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .thenReturn(example());

    mvc.perform(get(URL).header("Authorization", bearer("VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-11-01"))
        .andExpect(jsonPath("$.to").value("2026-11-30"))
        .andExpect(jsonPath("$.generatedAt").value("2026-11-20T09:15:00Z"))
        .andExpect(jsonPath("$.totalOrders").value(120))
        .andExpect(jsonPath("$.regions[0].country").value("UZB"))
        .andExpect(jsonPath("$.regions[0].countryName").value("Uzbekistan"))
        .andExpect(jsonPath("$.regions[0].currency").value("UZS"))
        .andExpect(jsonPath("$.regions[0].discount").value(2410.35))
        .andExpect(jsonPath("$.regions[0].deliveryFee").value(1460.00))
        .andExpect(jsonPath("$.regions[0].averageCheck").value(493.67));
  }

  @Test
  void adminGetsItToo() throws Exception {
    when(service.salesByRegion(any(), any())).thenReturn(example());
    mvc.perform(get(URL).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
  }

  @Test
  void guestIs401AndOtherRolesAre403() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    for (String role : new String[] {"CUSTOMER", "STAFF"}) {
      mvc.perform(get(URL).header("Authorization", bearer(role))).andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  @Test
  void missingOrBadDateIs400() throws Exception {
    mvc.perform(
            get("/api/v1/reports/sales-by-region?from=2026-11-01")
                .header("Authorization", bearer("VIEWER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.fields.to").exists());
    mvc.perform(
            get("/api/v1/reports/sales-by-region?from=1-11-2026&to=2026-11-30")
                .header("Authorization", bearer("VIEWER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.from").exists());
    verifyNoInteractions(service);
  }

  @Test
  void aWrongPeriodFromTheServiceIs400() throws Exception {
    when(service.salesByRegion(any(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                Map.of("to", "must be on or after from")));

    mvc.perform(
            get("/api/v1/reports/sales-by-region?from=2026-11-30&to=2026-11-01")
                .header("Authorization", bearer("VIEWER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.to").exists());
  }
}
