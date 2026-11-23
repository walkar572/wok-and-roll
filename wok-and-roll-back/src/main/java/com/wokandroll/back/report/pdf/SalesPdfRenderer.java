package com.wokandroll.back.report.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.dto.SalesTotals;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Draws the sales report PDF (api-contract.md section 3.18, #155, #173): A4 portrait, a header with
 * the filters and the UTC time, the orders-by-country chart and the region table, the year KPIs,
 * the total-by-month chart and the month table, "Page x of y" in the footer. It only prints what
 * ReportService gives, so PDF = web = CSV.
 */
@Component
public class SalesPdfRenderer {

  static final String TITLE = "Wok & Roll \u00b7 Sales report";

  private static final DateTimeFormatter GENERATED =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

  private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
  private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
  private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 9);
  private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
  private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8);
  private static final Color HEAD_BG = new Color(235, 235, 235);

  private static final String[] MONEY_HEADERS = {
    "Orders", "Subtotal", "VAT", "Discount", "Delivery fee", "Total", "Average check"
  };

  public byte[] render(SalesByRegionResponse regions, SalesByMonthResponse months) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 36, 36, 40, 50);
    try {
      PdfWriter writer = PdfWriter.getInstance(document, out);
      writer.setPageEvent(new PageNumbers());
      document.addTitle(TITLE);
      document.open();
      header(document, regions, months);
      regionSection(document, writer, regions);
      monthSection(document, writer, months);
      document.close();
    } catch (DocumentException e) {
      throw new IllegalStateException("Could not build the sales PDF", e);
    }
    return out.toByteArray();
  }

  private static void header(Document d, SalesByRegionResponse r, SalesByMonthResponse m) {
    d.add(new Paragraph(TITLE, TITLE_FONT));
    d.add(
        new Paragraph(
            "Sales by region: "
                + r.from()
                + " to "
                + r.to()
                + "    Sales by month: "
                + m.country()
                + " "
                + m.year(),
            TEXT));
    d.add(new Paragraph("Generated " + GENERATED.format(r.generatedAt()) + " UTC", TEXT));
  }

  private static void regionSection(Document d, PdfWriter writer, SalesByRegionResponse r) {
    d.add(section("1. Sales by region, " + r.from() + " to " + r.to()));
    d.add(
        new Paragraph(
            "Each country in its own currency, so there is no money total across countries. All"
                + " orders: "
                + r.totalOrders(),
            TEXT));
    d.add(chartTitle("Orders by country"));
    d.add(
        PdfBarChart.draw(
            writer,
            CHART_WIDTH,
            CHART_HEIGHT,
            r.regions().stream().map(RegionSales::country).toList(),
            r.regions().stream().map(s -> BigDecimal.valueOf(s.orders())).toList(),
            r.regions().stream().map(s -> String.valueOf(s.orders())).toList()));
    PdfPTable table = table("Country");
    for (RegionSales s : r.regions()) {
      table.addCell(cell(s.countryName() + " (" + s.currency() + ")", Element.ALIGN_LEFT));
      row(
          table,
          s.orders(),
          s.subtotal(),
          s.vat(),
          s.discount(),
          s.deliveryFee(),
          s.total(),
          s.averageCheck());
    }
    d.add(table);
  }

  private static void monthSection(Document d, PdfWriter writer, SalesByMonthResponse m) {
    d.add(
        section(
            "2. Sales by month, " + m.countryName() + " " + m.year() + " (" + m.currency() + ")"));
    SalesTotals t = m.totals();
    PdfPTable kpis = table("Year");
    kpis.addCell(cell(String.valueOf(m.year()), Element.ALIGN_LEFT));
    row(
        kpis,
        t.orders(),
        t.subtotal(),
        t.vat(),
        t.discount(),
        t.deliveryFee(),
        t.total(),
        t.averageCheck());
    d.add(kpis);
    d.add(chartTitle("Total by month, " + m.currency()));
    d.add(
        PdfBarChart.draw(
            writer,
            CHART_WIDTH,
            CHART_HEIGHT,
            m.months().stream().map(s -> monthShort(s.month())).toList(),
            m.months().stream().map(MonthSales::total).toList(),
            m.months().stream().map(s -> money(s.total())).toList()));
    PdfPTable table = table("Month");
    for (MonthSales s : m.months()) {
      table.addCell(cell(monthName(s.month()), Element.ALIGN_LEFT));
      row(
          table,
          s.orders(),
          s.subtotal(),
          s.vat(),
          s.discount(),
          s.deliveryFee(),
          s.total(),
          s.averageCheck());
    }
    d.add(table);
  }

  /** Width of the A4 text area (595 - 2 × 36), so the chart lines up with the tables. */
  private static final float CHART_WIDTH = 523;

  private static final float CHART_HEIGHT = 150;

  private static Paragraph chartTitle(String text) {
    Paragraph p = new Paragraph(text, HEAD);
    p.setSpacingBefore(8);
    p.setSpacingAfter(4);
    return p;
  }

  static String monthShort(int month) {
    return java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
  }

  private static Paragraph section(String text) {
    Paragraph p = new Paragraph(text, H2);
    p.setSpacingBefore(14);
    p.setSpacingAfter(6);
    return p;
  }

  private static PdfPTable table(String firstHeader) {
    PdfPTable table = new PdfPTable(new float[] {2.2f, 1f, 1.5f, 1.3f, 1.3f, 1.3f, 1.5f, 1.4f});
    table.setWidthPercentage(100);
    table.setHeaderRows(1);
    head(table, firstHeader);
    for (String h : MONEY_HEADERS) {
      head(table, h);
    }
    return table;
  }

  private static void head(PdfPTable table, String text) {
    PdfPCell c = new PdfPCell(new Phrase(text, HEAD));
    c.setBackgroundColor(HEAD_BG);
    c.setHorizontalAlignment(Element.ALIGN_CENTER);
    c.setPadding(4);
    table.addCell(c);
  }

  private static void row(PdfPTable table, long orders, BigDecimal... money) {
    table.addCell(cell(String.valueOf(orders), Element.ALIGN_RIGHT));
    for (BigDecimal value : money) {
      table.addCell(cell(money(value), Element.ALIGN_RIGHT));
    }
  }

  private static PdfPCell cell(String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text, CELL));
    c.setHorizontalAlignment(align);
    c.setPadding(4);
    return c;
  }

  /** Exactly 2 decimals, no grouping, the same digits as in the JSON. */
  static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  static String monthName(int month) {
    return java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  /** "Page x of y": y is written into a template when the document is closed. */
  private static final class PageNumbers extends PdfPageEventHelper {

    private PdfTemplate total;
    private BaseFont font;

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
      total = writer.getDirectContent().createTemplate(30, 12);
      try {
        font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
      } catch (Exception e) {
        throw new IllegalStateException("Helvetica is missing", e);
      }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      PdfContentByte cb = writer.getDirectContent();
      String text = "Page " + writer.getPageNumber() + " of ";
      float x = (document.right() + document.left()) / 2 - 20;
      float y = document.bottom() - 25;
      cb.beginText();
      cb.setFontAndSize(font, 8);
      cb.setTextMatrix(x, y);
      cb.showText(text);
      cb.endText();
      cb.addTemplate(total, x + font.getWidthPoint(text, 8), y);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
      total.beginText();
      total.setFontAndSize(font, 8);
      total.setTextMatrix(0, 0);
      total.showText(String.valueOf(writer.getPageNumber() - 1));
      total.endText();
    }
  }
}
