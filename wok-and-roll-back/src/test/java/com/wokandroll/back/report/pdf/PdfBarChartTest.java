package com.wokandroll.back.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.wokandroll.back.report.repository.FakeSalesReportRepository;
import com.wokandroll.back.report.repository.FakeSalesReportRepository.Order;
import com.wokandroll.back.report.service.ReportService;
import com.wokandroll.back.report.service.ReportServiceForTests;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The charts of the sales PDF (#173). */
class PdfBarChartTest {

  @Test
  void axisMaximumIsARoundNumberAtLeastTheBiggestValue() {
    assertThat(PdfBarChart.niceMax(0)).isZero();
    assertThat(PdfBarChart.niceMax(7)).isEqualTo(10);
    assertThat(PdfBarChart.niceMax(120)).isEqualTo(200);
    assertThat(PdfBarChart.niceMax(230)).isEqualTo(250);
    assertThat(PdfBarChart.niceMax(4100)).isEqualTo(5000);
    assertThat(PdfBarChart.niceMax(1000)).isEqualTo(1000);
  }

  @Test
  void axisLabelsAreShortForBigValues() {
    assertThat(PdfBarChart.axisLabel(0)).isEqualTo("0");
    assertThat(PdfBarChart.axisLabel(62.5)).isEqualTo("62.5");
    assertThat(PdfBarChart.axisLabel(5000)).isEqualTo("5000");
    assertThat(PdfBarChart.axisLabel(25000)).isEqualTo("25k");
    assertThat(PdfBarChart.axisLabel(2_500_000)).isEqualTo("2.5M");
  }

  @Test
  void everyBarHasItsLabelAndValueAsText() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4);
    PdfWriter writer = PdfWriter.getInstance(document, out);
    document.open();
    document.add(
        PdfBarChart.draw(
            writer,
            500,
            150,
            List.of("UZB", "KAZ"),
            List.of(new BigDecimal("12"), BigDecimal.ZERO),
            List.of("12", "0")));
    document.close();

    PdfReader reader = new PdfReader(out.toByteArray());
    String text = new PdfTextExtractor(reader).getTextFromPage(1);
    reader.close();
    assertThat(text).contains("UZB", "KAZ", "12");
  }

  @Test
  void labelsAndValuesMustMatch() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4);
    PdfWriter writer = PdfWriter.getInstance(document, out);
    document.open();
    assertThatThrownBy(
            () -> PdfBarChart.draw(writer, 500, 150, List.of("UZB"), List.of(), List.of("1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theSalesPdfHasBothChartsWithTheReportNumbers() throws Exception {
    ReportService service =
        ReportServiceForTests.at(
            "2026-11-20T09:15:42Z",
            new FakeSalesReportRepository()
                .add(
                    new Order(
                        "KAZ",
                        LocalDate.of(2026, 11, 10),
                        "NEW",
                        "70.00",
                        "8.40",
                        "0.00",
                        "9.90",
                        "88.30")));

    byte[] pdf =
        new SalesPdfRenderer()
            .render(
                service.salesByRegion(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                service.salesByMonth("KAZ", 2026));

    PdfReader reader = new PdfReader(pdf);
    PdfTextExtractor extractor = new PdfTextExtractor(reader);
    StringBuilder text = new StringBuilder();
    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
      text.append(extractor.getTextFromPage(page)).append('\n');
    }
    reader.close();
    assertThat(text)
        .contains("Orders by country", "Total by month, KZT", "Nov", "Jan", "CHN", "88.30");
  }
}
