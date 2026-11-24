package com.wokandroll.back.cafe.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the cafés with their opening hours and time zone (api-contract.md 3.6, #146). */
@Repository
public class CafeRepository {

  /**
   * One café. The opening hours are local times in the time zone of its country. The latitude and
   * longitude are both null when the café is not on the map yet.
   */
  public record CafeRow(
      long id,
      String countryCode,
      String name,
      String city,
      String address,
      String phone,
      LocalTime opensAt,
      LocalTime closesAt,
      int slotCapacity,
      String timeZone,
      Double latitude,
      Double longitude) {}

  private static final String ACTIVE_CAFES_SQL =
      """
      SELECT cf.id, c.code, cf.name, cf.city, cf.address, cf.phone, cf.opens_at, cf.closes_at,
             cf.slot_capacity, c.time_zone, cf.latitude, cf.longitude
      FROM cafe cf
      JOIN country c ON c.id = cf.country_id
      WHERE cf.active
      """;

  private final JdbcClient jdbc;

  public CafeRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Active cafés of the country, ordered by name. */
  public List<CafeRow> findAllActive(String countryCode) {
    return jdbc.sql(ACTIVE_CAFES_SQL + "AND c.code = :country\nORDER BY cf.name, cf.id\n")
        .param("country", countryCode)
        .query(CafeRepository::toRow)
        .list();
  }

  /** The café if it exists and is active, else empty. Time slots (#148) start here. */
  public Optional<CafeRow> findActive(long cafeId) {
    return jdbc.sql(ACTIVE_CAFES_SQL + "AND cf.id = :cafeId\n")
        .param("cafeId", cafeId)
        .query(CafeRepository::toRow)
        .optional();
  }

  private static CafeRow toRow(ResultSet rs, int rowNum) throws SQLException {
    return new CafeRow(
        rs.getLong("id"),
        rs.getString("code").trim(),
        rs.getString("name"),
        rs.getString("city"),
        rs.getString("address"),
        rs.getString("phone"),
        rs.getObject("opens_at", LocalTime.class),
        rs.getObject("closes_at", LocalTime.class),
        rs.getInt("slot_capacity"),
        rs.getString("time_zone"),
        degrees(rs.getBigDecimal("latitude")),
        degrees(rs.getBigDecimal("longitude")));
  }

  private static Double degrees(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
