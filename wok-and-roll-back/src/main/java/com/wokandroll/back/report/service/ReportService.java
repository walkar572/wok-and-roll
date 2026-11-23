package com.wokandroll.back.report.service;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.report.dto.MonthSales;
import com.wokandroll.back.report.dto.RegionSales;
import com.wokandroll.back.report.dto.SalesByMonthResponse;
import com.wokandroll.back.report.dto.SalesByRegionResponse;
import com.wokandroll.back.report.dto.SalesTotals;
import com.wokandroll.back.report.repository.CountryInfo;
import com.wokandroll.back.report.repository.MonthSalesRow;
import com.wokandroll.back.report.repository.RegionSalesRow;
import com.wokandroll.back.report.repository.SalesReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The sales reports (api-contract.md sections 3.16 to 3.18). The JSON API, the PDF (#155) and the
 * CSV (#69) all call these methods, so the numbers are the same everywhere.
 */
@Service
public class ReportService {

  /** Both days included, so a whole leap year fits (API4 in api-contract.md section 8). */
  static final long MAX_DAYS = 366;

  private final SalesReportRepository repository;
  private final Clock clock;

  @Autowired
  public ReportService(SalesReportRepository repository) {
    this(repository, Clock.systemUTC());
  }

  ReportService(SalesReportRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public SalesByRegionResponse salesByRegion(LocalDate from, LocalDate to) {
    checkPeriod(from, to);
    List<RegionSales> regions =
        repository.salesByRegion(from, to).stream().map(ReportService::toRegion).toList();
    long totalOrders = regions.stream().mapToLong(RegionSales::orders).sum();
    return new SalesByRegionResponse(
        from, to, clock.instant().truncatedTo(ChronoUnit.SECONDS), totalOrders, regions);
  }

  /** The first year with data (api-contract.md section 3.17). The last one is next year. */
  static final int FIRST_YEAR = 2024;

  public SalesByMonthResponse salesByMonth(String country, int year) {
    int lastYear = clock.instant().atZone(ZoneOffset.UTC).getYear() + 1;
    if (year < FIRST_YEAR || year > lastYear) {
      throw validation("year", "must be from " + FIRST_YEAR + " to " + lastYear);
    }
    CountryInfo info =
        repository
            .country(country)
            .orElseThrow(() -> validation("country", "must be one of UZB, KAZ, GEO, UKR, CHN"));
    List<MonthSalesRow> rows = repository.salesByMonth(country, year);
    List<MonthSales> months = rows.stream().map(ReportService::toMonth).toList();
    long orders = rows.stream().mapToLong(MonthSalesRow::orders).sum();
    BigDecimal total = sum(rows, MonthSalesRow::total);
    SalesTotals totals =
        new SalesTotals(
            orders,
            money(sum(rows, MonthSalesRow::subtotal)),
            money(sum(rows, MonthSalesRow::vat)),
            money(sum(rows, MonthSalesRow::discount)),
            money(sum(rows, MonthSalesRow::deliveryFee)),
            money(total),
            averageCheck(total, orders));
    return new SalesByMonthResponse(
        info.code(),
        info.name(),
        info.currency(),
        year,
        clock.instant().truncatedTo(ChronoUnit.SECONDS),
        months,
        totals);
  }

  private static MonthSales toMonth(MonthSalesRow row) {
    return new MonthSales(
        row.month(),
        row.orders(),
        money(row.subtotal()),
        money(row.vat()),
        money(row.discount()),
        money(row.deliveryFee()),
        money(row.total()),
        averageCheck(row.total(), row.orders()));
  }

  private static BigDecimal sum(
      List<MonthSalesRow> rows, java.util.function.Function<MonthSalesRow, BigDecimal> field) {
    return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static void checkPeriod(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw validation("to", "must be on or after from");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw validation("to", "the period can be at most 366 days");
    }
  }

  static BigDecimal averageCheck(BigDecimal total, long orders) {
    if (orders == 0) {
      return money(BigDecimal.ZERO);
    }
    return total.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
  }

  private static RegionSales toRegion(RegionSalesRow row) {
    return new RegionSales(
        row.country(),
        row.countryName(),
        row.currency(),
        row.orders(),
        money(row.subtotal()),
        money(row.vat()),
        money(row.discount()),
        money(row.deliveryFee()),
        money(row.total()),
        averageCheck(row.total(), row.orders()));
  }

  /** Always 2 decimals in the JSON, also for 0 from coalesce. */
  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static ApiException validation(String field, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Request validation failed",
        Map.of(field, message));
  }
}
