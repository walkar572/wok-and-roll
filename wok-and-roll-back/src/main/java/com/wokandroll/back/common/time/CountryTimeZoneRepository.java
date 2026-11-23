package com.wokandroll.back.common.time;

import java.time.ZoneId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the IANA time zone of a country from country.time_zone (#143). */
@Repository
public class CountryTimeZoneRepository {

  private final JdbcClient jdbc;

  public CountryTimeZoneRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Empty when there is no country with this code. */
  public Optional<ZoneId> find(String countryCode) {
    return jdbc.sql("SELECT time_zone FROM country WHERE code = :code")
        .param("code", countryCode)
        .query(String.class)
        .optional()
        .map(ZoneId::of);
  }
}
