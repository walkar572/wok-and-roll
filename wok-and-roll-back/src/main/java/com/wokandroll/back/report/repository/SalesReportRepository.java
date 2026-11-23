package com.wokandroll.back.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Reads order sums for the reports. An interface, so the service can be tested without a DB. */
public interface SalesReportRepository {

  /**
   * One row per country, in the order of GET /countries, also for countries without orders. Orders
   * with order_date from..to (both included) and a status other than CANCELLED.
   */
  List<RegionSalesRow> salesByRegion(LocalDate from, LocalDate to);

  /**
   * 12 rows, months 1 to 12, also for months without orders. Orders of the country with order_date
   * in that month of the year and a status other than CANCELLED.
   */
  List<MonthSalesRow> salesByMonth(String country, int year);

  /** Name and currency of a country code, empty for an unknown code. */
  Optional<CountryInfo> country(String code);
}
