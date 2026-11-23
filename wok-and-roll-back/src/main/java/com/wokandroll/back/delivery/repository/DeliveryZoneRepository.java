package com.wokandroll.back.delivery.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the delivery zones with their fixed fee and café (api-contract.md 3.7, #147). */
@Repository
public class DeliveryZoneRepository {

  /** One zone. The fee is in the local currency of the country, outside VAT. */
  public record DeliveryZoneRow(
      long id,
      String countryCode,
      String name,
      long cafeId,
      BigDecimal fee,
      String currency,
      int deliveryMinutes) {}

  // A zone can take orders when the zone and its café are both active.
  private static final String ACTIVE_ZONES_SQL =
      """
      SELECT z.id, c.code, z.name, z.cafe_id, z.fee, c.currency_code, z.delivery_minutes
      FROM delivery_zone z
      JOIN country c ON c.id = z.country_id
      JOIN cafe cf ON cf.id = z.cafe_id
      WHERE c.code = :country AND z.active AND cf.active
      """;

  private final JdbcClient jdbc;

  public DeliveryZoneRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Active zones of the country, ordered by name. */
  public List<DeliveryZoneRow> findAllActive(String countryCode) {
    return jdbc.sql(ACTIVE_ZONES_SQL + "ORDER BY z.name, z.id\n")
        .param("country", countryCode)
        .query(DeliveryZoneRepository::toRow)
        .list();
  }

  /**
   * The zone with this id if it is active and belongs to the country, else empty. The pricing
   * service reads the delivery fee here, so the fee in the quote is the fee in the zone list.
   */
  public Optional<DeliveryZoneRow> findActive(String countryCode, long zoneId) {
    return jdbc.sql(ACTIVE_ZONES_SQL + "AND z.id = :zoneId\n")
        .param("country", countryCode)
        .param("zoneId", zoneId)
        .query(DeliveryZoneRepository::toRow)
        .optional();
  }

  private static DeliveryZoneRow toRow(ResultSet rs, int rowNum) throws SQLException {
    return new DeliveryZoneRow(
        rs.getLong("id"),
        rs.getString("code").trim(),
        rs.getString("name"),
        rs.getLong("cafe_id"),
        rs.getBigDecimal("fee"),
        rs.getString("currency_code").trim(),
        rs.getInt("delivery_minutes"));
  }
}
