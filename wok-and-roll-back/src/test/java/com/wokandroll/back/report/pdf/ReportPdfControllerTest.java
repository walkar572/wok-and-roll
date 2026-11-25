package com.wokandroll.back.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@WebMvcTest(ReportPdfController.class)
@Import({SecurityConfig.class, SalesPdfRenderer.class})
class ReportPdfControllerTest {

  private static final String URL =
      "/api/v1/reports/sales.pdf?from=2026-11-01&to=2026-11-30&country=UZB&year=2026";

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwt;
  @MockitoBean private ReportService service;

  private final ReportService real =
      ReportServiceForTests.at("2026-11-20T09:15:00Z", new FakeSalesReportRepository());

  private String bearer(String role) {
    return "Bearer " + jwt.createToken(new AuthUser(1, "user@wokandroll.test", role));
  }

  @Test
  void viewerDownloadsThePdfWithTheContractFileName() throws Exception {
    LocalDate from = LocalDate.of(2026, 11, 1);
    LocalDate to = LocalDate.of(2026, 11, 30);
    when(service.salesByRegion(from, to)).thenReturn(real.salesByRegion(from, to));
    when(service.salesByMonth("UZB", 2026)).thenReturn(real.salesByMonth("UZB", 2026));

    byte[] body =
        mvc.perform(get(URL).header("Authorization", bearer("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"wok-and-roll-sales_2026-11-01_2026-11-30_UZB-2026.pdf\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(new String(body, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @Test
  void askingThePdfEndpointForJsonIs406() throws Exception {
    mvc.perform(
            get(URL).header("Authorization", bearer("VIEWER")).header("Accept", "application/json"))
        .andExpect(status().isNotAcceptable())
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    verifyNoInteractions(service);
  }

  @Test
  void guestIs401AndCustomerIs403() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    mvc.perform(get(URL).header("Authorization", bearer("CUSTOMER")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void allFourParametersAreRequired() throws Exception {
    mvc.perform(
            get("/api/v1/reports/sales.pdf?from=2026-11-01&to=2026-11-30&country=UZB")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.year").exists());
    mvc.perform(
            get("/api/v1/reports/sales.pdf?from=2026-11-01&to=2026-11-30&country=FRA&year=2026")
                .header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(service);
  }

  @Test
  void fileNameFollowsTheContract() {
    assertThat(
            ReportPdfController.fileName(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "UZB", 2026))
        .isEqualTo("wok-and-roll-sales_2026-11-01_2026-11-30_UZB-2026.pdf");
  }
}
