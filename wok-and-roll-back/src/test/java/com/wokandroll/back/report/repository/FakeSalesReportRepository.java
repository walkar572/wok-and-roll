package com.wokandroll.back.report.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fixture orders in memory, with the same rules as the SQL: the period with both days, no
 * CANCELLED, every country, in the order of GET /countries.
 */
public class FakeSalesReportRepository implements SalesReportRepository {

  /** A saved order, only the fields the report reads. */
  public record Order(
      String country,
      LocalDate orderDate,
      String status,
      String subtotal,
      String vat,
      String discount,
      String deliveryFee,
      String total) {}

  private static final String[][] COUNTRIES = {
    {"UZB", "Uzbekistan", "UZS"},
    {"KAZ", "Kazakhstan", "KZT"},
    {"GEO", "Georgia", "GEL"},
    {"UKR", "Ukraine", "UAH"},
    {"CHN", "China", "CNY"}
  };

  private final List<Order> orders = new ArrayList<>();

  public FakeSalesReportRepository add(Order order) {
    orders.add(order);
    return this;
  }

  @Override
  public List<RegionSalesRow> salesByRegion(LocalDate from, LocalDate to) {
    Map<String, List<Order>> byCountry = new LinkedHashMap<>();
    for (String[] c : COUNTRIES) {
      byCountry.put(c[0], new ArrayList<>());
    }
    for (Order o : orders) {
      boolean inPeriod = !o.orderDate().isBefore(from) && !o.orderDate().isAfter(to);
      if (inPeriod && !"CANCELLED".equals(o.status())) {
        byCountry.get(o.country()).add(o);
      }
    }
    List<RegionSalesRow> rows = new ArrayList<>();
    for (String[] c : COUNTRIES) {
      List<Order> list = byCountry.get(c[0]);
      rows.add(
          new RegionSalesRow(
              c[0],
              c[1],
              c[2],
              list.size(),
              sum(list, Order::subtotal),
              sum(list, Order::vat),
              sum(list, Order::discount),
              sum(list, Order::deliveryFee),
              sum(list, Order::total)));
    }
    return rows;
  }

  private static BigDecimal sum(
      List<Order> list, java.util.function.Function<Order, String> field) {
    return list.stream().map(field).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Override
  public List<MonthSalesRow> salesByMonth(String country, int year) {
    List<MonthSalesRow> rows = new ArrayList<>();
    for (int month = 1; month <= 12; month++) {
      int m = month;
      List<Order> list =
          orders.stream()
              .filter(o -> o.country().equals(country))
              .filter(o -> o.orderDate().getYear() == year && o.orderDate().getMonthValue() == m)
              .filter(o -> !"CANCELLED".equals(o.status()))
              .toList();
      rows.add(
          new MonthSalesRow(
              month,
              list.size(),
              sum(list, Order::subtotal),
              sum(list, Order::vat),
              sum(list, Order::discount),
              sum(list, Order::deliveryFee),
              sum(list, Order::total)));
    }
    return rows;
  }

  @Override
  public Optional<CountryInfo> country(String code) {
    for (String[] c : COUNTRIES) {
      if (c[0].equals(code)) {
        return Optional.of(new CountryInfo(c[0], c[1], c[2]));
      }
    }
    return Optional.empty();
  }
}
