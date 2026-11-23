package com.wokandroll.back.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL over customer_order (#144). The query is the reference query of api-contract.md section
 * 3.16 plus the country name and currency, so QA can compare the numbers one to one.
 */
@Repository
public class JdbcSalesReportRepository implements SalesReportRepository {

  private final JdbcClient jdbc;

  public JdbcSalesReportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<RegionSalesRow> salesByRegion(LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT c.code, c.name, c.currency_code,
                   count(o.id)                       AS orders,
                   coalesce(sum(o.subtotal), 0)        AS subtotal,
                   coalesce(sum(o.vat_amount), 0)      AS vat,
                   coalesce(sum(o.discount_amount), 0) AS discount,
                   coalesce(sum(o.delivery_fee), 0)    AS delivery_fee,
                   coalesce(sum(o.total), 0)           AS total
            FROM country c
            LEFT JOIN customer_order o
                   ON o.country_id = c.id
                  AND o.order_date BETWEEN :from AND :to
                  AND o.status <> 'CANCELLED'
            GROUP BY c.id, c.code, c.name, c.currency_code
            ORDER BY c.id
            """)
        .param("from", from)
        .param("to", to)
        .query(
            (rs, i) ->
                new RegionSalesRow(
                    rs.getString("code").trim(),
                    rs.getString("name"),
                    rs.getString("currency_code").trim(),
                    rs.getLong("orders"),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("vat"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("delivery_fee"),
                    rs.getBigDecimal("total")))
        .list();
  }

  @Override
  public List<MonthSalesRow> salesByMonth(String country, int year) {
    // the month bounds are compared with order_date directly, so the (country_id, order_date)
    // index of #144 is used
    return jdbc.sql(
            """
            SELECT m.month,
                   count(o.id)                       AS orders,
                   coalesce(sum(o.subtotal), 0)        AS subtotal,
                   coalesce(sum(o.vat_amount), 0)      AS vat,
                   coalesce(sum(o.discount_amount), 0) AS discount,
                   coalesce(sum(o.delivery_fee), 0)    AS delivery_fee,
                   coalesce(sum(o.total), 0)           AS total
            FROM generate_series(1, 12) AS m (month)
            LEFT JOIN customer_order o
                   ON o.country_id = (SELECT c.id FROM country c WHERE c.code = :country)
                  AND o.order_date >= make_date(:year, m.month, 1)
                  AND o.order_date < (make_date(:year, m.month, 1) + INTERVAL '1 month')::date
                  AND o.status <> 'CANCELLED'
            GROUP BY m.month
            ORDER BY m.month
            """)
        .param("country", country)
        .param("year", year)
        .query(
            (rs, i) ->
                new MonthSalesRow(
                    rs.getInt("month"),
                    rs.getLong("orders"),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("vat"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("delivery_fee"),
                    rs.getBigDecimal("total")))
        .list();
  }

  @Override
  public Optional<CountryInfo> country(String code) {
    return jdbc.sql("SELECT code, name, currency_code FROM country WHERE code = :code")
        .param("code", code)
        .query(
            (rs, i) ->
                new CountryInfo(
                    rs.getString("code").trim(),
                    rs.getString("name"),
                    rs.getString("currency_code").trim()))
        .optional();
  }
}
