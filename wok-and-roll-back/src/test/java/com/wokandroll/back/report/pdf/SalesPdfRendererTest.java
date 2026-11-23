package com.wokandroll.back.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.repository.FakeSalesReportRepository.Order;
import com.wokandroll.back.report.service.ReportService;
import com.wokandroll.back.report.service.ReportServiceForTests;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The PDF shows the same numbers as the JSON (api-contract.md section 3.18). */
class SalesPdfRendererTest {

  private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
  private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

  private final FakeSalesReportRepository orders =
      new FakeSalesReportRepository()
          .add(order("UZB", "2026-11-03", "NEW", "100.00", "15.00", "3.33", "12.00", "123.67"))
          .add(order("UZB", "2026-11-20", "DONE", "250.50", "37.58", "0.00", "0.00", "288.08"))
          .add(order("UZB", "2026-02-14", "DONE", "40.00", "6.00", "0.00", "5.00", "51.00"))
          .add(order("KAZ", "2026-11-10", "CONFIRMED", "70.00", "8.40", "0.00", "9.90", "88.30"))
          .add(order("CHN", "2026-11-11", "NEW", "33.10", "4.30", "0.00", "0.00", "37.40"));

  private final ReportService service = ReportServiceForTests.at("2026-11-20T09:15:42Z", orders);

  private static Order order(
      String country,
      String day,
      String status,
      String sub,
      String vat,
      String disc,
      String fee,
      String total) {
    return new Order(country, LocalDate.parse(day), status, sub, vat, disc, fee, total);
  }

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    try {
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder all = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        all.append(extractor.getTextFromPage(page)).append('\n');
      }
      return all.toString();
    } finally {
      reader.close();
    }
  }

  @Test
  void thePdfHasTheHeaderAndEveryNumberOfBothReports() throws Exception {
    SalesByRegionResponse regions = service.salesByRegion(NOV_1, NOV_30);
    SalesByMonthResponse months = service.salesByMonth("UZB", 2026);

    byte[] pdf = new SalesPdfRenderer().render(regions, months);

    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    String text = text(pdf);
    assertThat(text).contains("Sales report");
    assertThat(text).contains("2026-11-01 to 2026-11-30");
    assertThat(text).contains("Generated 2026-11-20 09:15 UTC");
    assertThat(text).contains("Page 1 of");

    for (RegionSales r : regions.regions()) {
      assertThat(text).contains(r.countryName() + " (" + r.currency() + ")");
      for (BigDecimal value :
          new BigDecimal[] {
            r.subtotal(), r.vat(), r.discount(), r.deliveryFee(), r.total(), r.averageCheck()
          }) {
        assertThat(text).contains(SalesPdfRenderer.money(value));
      }
    }
    for (MonthSales m : months.months()) {
      assertThat(text).contains(SalesPdfRenderer.monthName(m.month()));
      assertThat(text).contains(SalesPdfRenderer.money(m.total()));
    }
    assertThat(text).contains(SalesPdfRenderer.money(months.totals().total()));
    assertThat(text).contains(SalesPdfRenderer.money(months.totals().averageCheck()));
    // the values from the fixture, so the test also fails if the service returned zeros
    assertThat(text).contains("411.75", "205.88", "88.30", "37.40", "462.75");
  }

  @Test
  void moneyHasExactlyTwoDecimalsAndNoGrouping() {
    assertThat(SalesPdfRenderer.money(new BigDecimal("52340"))).isEqualTo("52340.00");
    assertThat(SalesPdfRenderer.money(new BigDecimal("0.5"))).isEqualTo("0.50");
    assertThat(SalesPdfRenderer.money(new BigDecimal("1234567.89"))).isEqualTo("1234567.89");
  }

  @Test
  void monthsAreInEnglish() {
    assertThat(SalesPdfRenderer.monthName(1)).isEqualTo("January");
    assertThat(SalesPdfRenderer.monthName(12)).isEqualTo("December");
  }
}
