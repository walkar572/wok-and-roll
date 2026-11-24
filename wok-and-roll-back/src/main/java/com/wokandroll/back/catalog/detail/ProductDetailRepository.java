package com.wokandroll.back.catalog.detail;

import com.wokandroll.back.catalog.detail.dto.ModifierResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads one dish and its modifiers for a country. Plain SQL with named parameters (JdbcClient), the
 * same approach as the pricing package, so it does not clash with catalog entities.
 */
@Repository
public class ProductDetailRepository {

  /** Product row in one country, before the modifiers are added. */
  public record ProductRow(
      long id,
      long categoryId,
      String name,
      String description,
      String imageUrl,
      Integer pieces,
      Integer weightGrams,
      Integer volumeMl,
      BigDecimal price,
      String currency,
      boolean inStock) {}

  private final JdbcClient jdbc;

  public ProductDetailRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Empty when the dish does not exist, the dish or its category is hidden, or it has no price in
   * this country. Same rule as the menu list (CatalogRepository), so the modal never opens a dish
   * that the menu does not show.
   */
  public Optional<ProductRow> findProduct(long productId, String countryCode) {
    return jdbc.sql(
            """
            SELECT p.id, p.category_id, p.name, p.description, p.image_url,
                   p.pieces, p.weight_grams, p.volume_ml, rp.price, c.currency_code, rp.in_stock
            FROM product p
            JOIN category cat ON cat.id = p.category_id
            JOIN regional_price rp ON rp.product_id = p.id
            JOIN country c ON c.id = rp.country_id
            WHERE p.id = :productId AND c.code = :country AND p.visible AND cat.visible
            """)
        .param("productId", productId)
        .param("country", countryCode)
        .query(
            (rs, i) ->
                new ProductRow(
                    rs.getLong("id"),
                    rs.getLong("category_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("image_url"),
                    rs.getObject("pieces", Integer.class),
                    rs.getObject("weight_grams", Integer.class),
                    rs.getObject("volume_ml", Integer.class),
                    rs.getBigDecimal("price"),
                    rs.getString("currency_code").trim(),
                    rs.getBoolean("in_stock")))
        .optional();
  }

  /** Visible modifiers with a price in this country. Order: SIZE, then EXTRA, then id. */
  public List<ModifierResponse> findModifiers(long productId, String countryCode) {
    return jdbc.sql(
            """
            SELECT m.id, m.type, m.name, mp.price_delta
            FROM modifier m
            JOIN modifier_price mp ON mp.modifier_id = m.id
            JOIN country c ON c.id = mp.country_id
            WHERE m.product_id = :productId AND c.code = :country AND m.visible
            ORDER BY CASE m.type WHEN 'SIZE' THEN 0 ELSE 1 END, m.id
            """)
        .param("productId", productId)
        .param("country", countryCode)
        .query(
            (rs, i) ->
                new ModifierResponse(
                    rs.getLong("id"),
                    rs.getString("type"),
                    rs.getString("name"),
                    rs.getBigDecimal("price_delta")))
        .list();
  }
}
