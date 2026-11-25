package com.wokandroll.back.report.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.repository.FakeSalesReportRepository.Order;
import com.wokandroll.back.report.service.ReportService;
import com.wokandroll.back.report.service.ReportServiceForTests;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalesCsvWriterTest {

  private final SalesCsvWriter writer = new SalesCsvWriter();

  private final ReportService reports =
      ReportServiceForTests.at(
          "2026-11-20T09:15:00Z",
          new FakeSalesReportRepository()
              .add(
                  new Order(
                      "UZB",
                      LocalDate.of(2026, 11, 3),
                      "NEW",
                      "170.00",
                      "25.50",
                      "5.87",
                      "20.00",
                      "209.63"))
              .add(
                  new Order(
                      "UZB",
                      LocalDate.of(2026, 11, 4),
                      "DONE",
                      "100.00",
                      "15.00",
                      "3.45",
                      "0.00",
                      "111.55")));

  @Test
  void regionReportHasTheHeaderAndAllFiveCountriesWithCrlf() {
    SalesByRegionResponse report =
        reports.salesByRegion(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

    String csv = writer.salesByRegion(report);

    String[] lines = csv.split("\r\n", -1);
    assertThat(lines).hasSize(7); // header, 5 countries, empty after the last CRLF
    assertThat(lines[0])
        .isEqualTo(
            "country,countryName,currency,orders,subtotal,vat,discount,deliveryFee,total,averageCheck");
    assertThat(lines[1]).isEqualTo("UZB,Uzbekistan,UZS,2,270.00,40.50,9.32,20.00,321.18,160.59");
    assertThat(lines[2]).isEqualTo("KAZ,Kazakhstan,KZT,0,0.00,0.00,0.00,0.00,0.00,0.00");
    assertThat(lines[6]).isEmpty();
    assertThat(csv).doesNotContain("\n\n").doesNotContainPattern("[^\r]\n");
  }

  @Test
  void monthReportHasTwelveMonthsAsYearMonth() {
    SalesByMonthResponse report = reports.salesByMonth("UZB", 2026);

    String[] lines = writer.salesByMonth(report).split("\r\n");

    assertThat(lines).hasSize(13);
    assertThat(lines[0])
        .isEqualTo("month,orders,subtotal,vat,discount,deliveryFee,total,averageCheck");
    assertThat(lines[1]).isEqualTo("2026-01,0,0.00,0.00,0.00,0.00,0.00,0.00");
    assertThat(lines[11]).isEqualTo("2026-11,2,270.00,40.50,9.32,20.00,321.18,160.59");
    assertThat(lines[12]).startsWith("2026-12,0,");
  }

  @Test
  void numbersInTheCsvAreTheNumbersOfTheJson() {
    SalesByRegionResponse report =
        reports.salesByRegion(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    String uzb = writer.salesByRegion(report).split("\r\n")[1];

    assertThat(uzb.split(",")[8]).isEqualTo(report.regions().get(0).total().toPlainString());
  }

  @Test
  void textCellsAreSafeAgainstCsvInjection() {
    assertThat(SalesCsvWriter.text("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    assertThat(SalesCsvWriter.text("+1")).isEqualTo("'+1");
    assertThat(SalesCsvWriter.text("-1")).isEqualTo("'-1");
    assertThat(SalesCsvWriter.text("@SUM")).isEqualTo("'@SUM");
    assertThat(SalesCsvWriter.text("\tTab")).isEqualTo("'\tTab");
    assertThat(SalesCsvWriter.text("Uzbekistan")).isEqualTo("Uzbekistan");
  }

  @Test
  void textCellsWithCommaQuoteOrLineBreakAreQuoted() {
    assertThat(SalesCsvWriter.text("Wok, Roll")).isEqualTo("\"Wok, Roll\"");
    assertThat(SalesCsvWriter.text("a \"b\"")).isEqualTo("\"a \"\"b\"\"\"");
    assertThat(SalesCsvWriter.text("a\nb")).isEqualTo("\"a\nb\"");
    assertThat(SalesCsvWriter.text(null)).isEmpty();
  }

  @Test
  void moneyHasExactlyTwoDecimalsAndNoGrouping() {
    assertThat(SalesCsvWriter.money(new BigDecimal("1234567.5"))).isEqualTo("1234567.50");
    assertThat(SalesCsvWriter.money(new BigDecimal("0"))).isEqualTo("0.00");
    assertThat(SalesCsvWriter.money(new BigDecimal("5.865"))).isEqualTo("5.87");
  }
}
