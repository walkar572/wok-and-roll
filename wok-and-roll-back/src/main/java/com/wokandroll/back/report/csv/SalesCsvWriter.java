package com.wokandroll.back.report.csv;

import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes the sales reports as CSV (api-contract.md section 3.19, #172). It only prints what
 * ReportService gives, so CSV = web = PDF.
 *
 * <ul>
 *   <li>RFC 4180: a header row, comma separator, CRLF line ends, UTF-8.
 *   <li>Money with exactly 2 decimals and "." as the decimal point, no thousands separator.
 *   <li>A text cell with a comma, a quote or a line break is quoted; inner quotes are doubled.
 *   <li>CSV injection (OWASP): a text cell that starts with =, +, -, @, a tab or CR gets a leading
 *       apostrophe, so a spreadsheet never runs it as a formula. Numbers are written as numbers.
 * </ul>
 */
@Component
public class SalesCsvWriter {

  static final String CRLF = "\r\n";

  static final List<String> REGION_HEADER =
      List.of(
          "country",
          "countryName",
          "currency",
          "orders",
          "subtotal",
          "vat",
          "discount",
          "deliveryFee",
          "total",
          "averageCheck");

  static final List<String> MONTH_HEADER =
      List.of(
          "month", "orders", "subtotal", "vat", "discount", "deliveryFee", "total", "averageCheck");

  public String salesByRegion(SalesByRegionResponse report) {
    StringBuilder csv = new StringBuilder();
    line(csv, REGION_HEADER);
    for (RegionSales r : report.regions()) {
      line(
          csv,
          List.of(
              text(r.country()),
              text(r.countryName()),
              text(r.currency()),
              String.valueOf(r.orders()),
              money(r.subtotal()),
              money(r.vat()),
              money(r.discount()),
              money(r.deliveryFee()),
              money(r.total()),
              money(r.averageCheck())));
    }
    return csv.toString();
  }

  public String salesByMonth(SalesByMonthResponse report) {
    StringBuilder csv = new StringBuilder();
    line(csv, MONTH_HEADER);
    for (MonthSales m : report.months()) {
      line(
          csv,
          List.of(
              String.format("%04d-%02d", report.year(), m.month()),
              String.valueOf(m.orders()),
              money(m.subtotal()),
              money(m.vat()),
              money(m.discount()),
              money(m.deliveryFee()),
              money(m.total()),
              money(m.averageCheck())));
    }
    return csv.toString();
  }

  private static void line(StringBuilder csv, List<String> cells) {
    csv.append(String.join(",", cells)).append(CRLF);
  }

  /** Exactly 2 decimals, the same digits as in the JSON and the PDF. */
  static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  /** A text cell, safe against CSV injection and quoted when RFC 4180 needs it. */
  static String text(String value) {
    if (value == null) {
      return "";
    }
    String safe = value;
    if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
      safe = "'" + safe;
    }
    boolean needsQuotes =
        safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
    return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
  }
}
