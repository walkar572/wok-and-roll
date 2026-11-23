package com.wokandroll.back.report.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesTotals;
import com.wokandroll.back.report.service.ReportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class SalesByMonthControllerTest {

  private static final String URL = "/api/v1/reports/sales-by-month?country=UZB&year=2026";

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockitoBean private ReportService service;

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "user@wokandroll.test", role));
  }

  private static SalesByMonthResponse example() {
    BigDecimal zero = new BigDecimal("0.00");
    List<MonthSales> months = new ArrayList<>();
    for (int m = 1; m <= 12; m++) {
      months.add(new MonthSales(m, 0, zero, zero, zero, zero, zero, zero));
    }
    return new SalesByMonthResponse(
        "UZB",
        "Uzbekistan",
        "UZS",
        2026,
        Instant.parse("2026-11-20T09:15:00Z"),
        months,
        new SalesTotals(
            640,
            new BigDecimal("281120.00"),
            new BigDecimal("42168.00"),
            new BigDecimal("13210.40"),
            new BigDecimal("7840.00"),
            new BigDecimal("317917.60"),
            new BigDecimal("496.75")));
  }

  @Test
  void viewerGetsTheReportInTheContractShape() throws Exception {
    when(service.salesByMonth("UZB", 2026)).thenReturn(example());

    mvc.perform(get(URL).header("Authorization", bearer("VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.country").value("UZB"))
        .andExpect(jsonPath("$.countryName").value("Uzbekistan"))
        .andExpect(jsonPath("$.currency").value("UZS"))
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.generatedAt").value("2026-11-20T09:15:00Z"))
        .andExpect(jsonPath("$.months.length()").value(12))
        .andExpect(jsonPath("$.months[0].month").value(1))
        .andExpect(jsonPath("$.months[11].month").value(12))
        .andExpect(jsonPath("$.totals.orders").value(640))
        .andExpect(jsonPath("$.totals.total").value(317917.60))
        .andExpect(jsonPath("$.totals.averageCheck").value(496.75));
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
  void unknownCountryIs400() throws Exception {
    mvc.perform(
            get("/api/v1/reports/sales-by-month?country=USA&year=2026")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(service);
  }

  @Test
  void missingOrBadYearIs400() throws Exception {
    mvc.perform(
            get("/api/v1/reports/sales-by-month?country=UZB")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.year").exists());
    mvc.perform(
            get("/api/v1/reports/sales-by-month?country=UZB&year=abc")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.year").exists());
    verifyNoInteractions(service);
  }

  @Test
  void adminGetsItToo() throws Exception {
    when(service.salesByMonth(anyString(), anyInt())).thenReturn(example());
    mvc.perform(get(URL).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
  }
}
