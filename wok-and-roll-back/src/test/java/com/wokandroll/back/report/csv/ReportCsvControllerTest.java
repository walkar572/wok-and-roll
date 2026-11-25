package com.wokandroll.back.report.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wokandroll.back.auth.AuthUser;
import com.wokandroll.back.auth.JwtService;
import com.wokandroll.back.auth.SecurityConfig;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.service.ReportService;
import com.wokandroll.back.report.service.ReportServiceForTests;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportCsvController.class)
@Import({SecurityConfig.class, SalesCsvWriter.class})
class ReportCsvControllerTest {

  private static final String REGION_URL =
      "/api/v1/reports/sales-by-region.csv?from=2026-11-01&to=2026-11-30";
  private static final String MONTH_URL =
      "/api/v1/reports/sales-by-month.csv?country=UZB&year=2026";

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockitoBean private ReportService service;

  private final ReportService real =
      ReportServiceForTests.at("2026-11-20T09:15:00Z", new FakeSalesReportRepository());

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "user@wokandroll.test", role));
  }

  @Test
  void viewerDownloadsTheRegionCsvWithTheContractFileName() throws Exception {
    LocalDate from = LocalDate.of(2026, 11, 1);
    LocalDate to = LocalDate.of(2026, 11, 30);
    when(service.salesByRegion(from, to)).thenReturn(real.salesByRegion(from, to));

    byte[] body =
        mvc.perform(get(REGION_URL).header("Authorization", bearer("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"wok-and-roll-sales-by-region_2026-11-01_2026-11-30.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(new String(body, StandardCharsets.UTF_8))
        .startsWith("country,countryName,currency,orders,")
        .contains("\r\nUZB,Uzbekistan,UZS,0,0.00,");
  }

  @Test
  void adminDownloadsTheMonthCsvWithTheContractFileName() throws Exception {
    when(service.salesByMonth("UZB", 2026)).thenReturn(real.salesByMonth("UZB", 2026));

    String body =
        mvc.perform(get(MONTH_URL).header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"wok-and-roll-sales-by-month_UZB_2026.csv\""))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body.split("\r\n")).hasSize(13);
  }

  @Test
  void guestIs401AndCustomerOrStaffIs403() throws Exception {
    mvc.perform(get(REGION_URL)).andExpect(status().isUnauthorized());
    mvc.perform(get(MONTH_URL).header("Authorization", bearer("CUSTOMER")))
        .andExpect(status().isForbidden());
    mvc.perform(get(MONTH_URL).header("Authorization", bearer("STAFF")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void parametersFollowTheReportRules() throws Exception {
    mvc.perform(
            get("/api/v1/reports/sales-by-month.csv?country=FRA&year=2026")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(
            get("/api/v1/reports/sales-by-region.csv?from=2026-11-01")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.to").exists());
    verifyNoInteractions(service);
  }

  @Test
  void fileNamesFollowTheContract() {
    assertThat(
            ReportCsvController.regionFileName(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        .isEqualTo("wok-and-roll-sales-by-region_2026-11-01_2026-11-30.csv");
    assertThat(ReportCsvController.monthFileName("UZB", 2026))
        .isEqualTo("wok-and-roll-sales-by-month_UZB_2026.csv");
  }
}
