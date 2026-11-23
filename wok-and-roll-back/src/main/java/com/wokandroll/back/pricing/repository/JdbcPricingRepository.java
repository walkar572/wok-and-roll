package com.wokandroll.back.pricing.repository;

import com.wokandroll.back.pricing.dto.DiscountTier;
import com.wokandroll.back.pricing.dto.ModifierType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL on purpose: pricing only reads a few columns of tables that other packages (catalog,
 * country) own, so it does not duplicate their JPA entities.
 */
@Repository
public class JdbcPricingRepository implements PricingRepository {

  private final JdbcClient jdbc;

  public JdbcPricingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CountryPricing> findCountry(String code) {
    return jdbc.sql(
            "SELECT c.id, c.code, c.currency_code, v.rate AS vat_rate "
                + "FROM country c LEFT JOIN vat_rate v ON v.country_id = c.id "
                + "WHERE c.code = :code")
        .param("code", code)
        .query(
            (rs, rowNum) ->
                new CountryPricing(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("vat_rate")))
        .optional();
  }

  @Override
  public List<ProductPrice> findProducts(long countryId, Collection<Long> productIds) {
    if (productIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "SELECT p.id, p.name, (p.visible AND cat.visible) AS visible, "
                + "rp.price, COALESCE(rp.in_stock, FALSE) AS in_stock "
                + "FROM product p "
                + "JOIN category cat ON cat.id = p.category_id "
                + "LEFT JOIN regional_price rp "
                + "ON rp.product_id = p.id AND rp.country_id = :countryId "
                + "WHERE p.id IN (:ids)")
        .param("countryId", countryId)
        .param("ids", productIds)
        .query(
            (rs, rowNum) ->
                new ProductPrice(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getBoolean("visible"),
                    rs.getBigDecimal("price"),
                    rs.getBoolean("in_stock")))
        .list();
  }

  @Override
  public List<ModifierPrice> findModifiers(long countryId, Collection<Long> modifierIds) {
    if (modifierIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "SELECT m.id, m.product_id, m.type, m.name, m.visible, mp.price_delta "
                + "FROM modifier m "
                + "LEFT JOIN modifier_price mp "
                + "ON mp.modifier_id = m.id AND mp.country_id = :countryId "
                + "WHERE m.id IN (:ids)")
        .param("countryId", countryId)
        .param("ids", modifierIds)
        .query(
            (rs, rowNum) ->
                new ModifierPrice(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    ModifierType.valueOf(rs.getString("type")),
                    rs.getString("name"),
                    rs.getBoolean("visible"),
                    rs.getBigDecimal("price_delta")))
        .list();
  }

  @Override
  public List<Long> findProductIdsWithSizes(long countryId, Collection<Long> productIds) {
    if (productIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            "SELECT DISTINCT m.product_id "
                + "FROM modifier m "
                + "JOIN modifier_price mp "
                + "ON mp.modifier_id = m.id AND mp.country_id = :countryId "
                + "WHERE m.type = 'SIZE' AND m.visible AND m.product_id IN (:ids)")
        .param("countryId", countryId)
        .param("ids", productIds)
        .query((rs, rowNum) -> rs.getLong("product_id"))
        .list();
  }

  @Override
  public List<DiscountTier> findDiscountTiers(long countryId) {
    return jdbc.sql(
            "SELECT min_amount, discount_rate FROM discount_threshold "
                + "WHERE country_id = :countryId ORDER BY min_amount")
        .param("countryId", countryId)
        .query(
            (rs, rowNum) ->
                new DiscountTier(rs.getBigDecimal("min_amount"), rs.getBigDecimal("discount_rate")))
        .list();
  }
}
