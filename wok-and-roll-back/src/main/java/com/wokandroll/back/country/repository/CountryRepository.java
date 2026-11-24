package com.wokandroll.back.country.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the countries with their currency, VAT rate and time zone. Plain SQL with JdbcClient. */
@Repository
public class CountryRepository {

  /** One country with its VAT rate in percent, its IANA time zone and its café cities. */
  public record CountryRow(
      String code,
      String name,
      String currency,
      BigDecimal vatRate,
      String timeZone,
      List<String> cities) {}

  private final JdbcClient jdbc;

  public CountryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** All countries that have a VAT rate, in the order of the seed (UZB first). */
  public List<CountryRow> findAll() {
    return jdbc.sql(
            """
            SELECT c.code, c.name, c.currency_code, v.rate, c.time_zone,
                   ARRAY(SELECT DISTINCT cf.city
                         FROM cafe cf
                         WHERE cf.country_id = c.id AND cf.active
                         ORDER BY cf.city) AS cities
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
                    rs.getString("time_zone"),
                    strings(rs.getArray("cities"))))
        .list();
  }

  private static List<String> strings(Array array) throws SQLException {
    return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
  }
}
