package com.wokandroll.back.country.repository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the countries with their currency, VAT rate and time zone. Plain SQL with JdbcClient. */
@Repository
public class CountryRepository {

  /** One country with its VAT rate in percent and its IANA time zone. */
  public record CountryRow(
      String code, String name, String currency, BigDecimal vatRate, String timeZone) {}

  private final JdbcClient jdbc;

  public CountryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** All countries that have a VAT rate, in the order of the seed (UZB first). */
  public List<CountryRow> findAll() {
    return jdbc.sql(
            """
            SELECT c.code, c.name, c.currency_code, v.rate, c.time_zone
            FROM country c
            JOIN vat_rate v ON v.country_id = c.id
            ORDER BY c.id
            """)
        .query(
            (rs, i) ->
                new CountryRow(
                    rs.getString("code").trim(),
                    rs.getString("name"),
                    rs.getString("currency_code").trim(),
                    rs.getBigDecimal("rate"),
                    rs.getString("time_zone")))
        .list();
  }
}
