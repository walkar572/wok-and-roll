package com.wokandroll.back.catalog.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the menu of one country: categories and dishes with their regional price. */
@Repository
public class CatalogRepository {

  /** A category that has at least one dish in the country. */
  public record CategoryRow(long id, String name) {}

  /** A dish with its price, currency and stock in one country. */
  public record ProductRow(
      long id,
      long categoryId,
      String name,
      String description,
      String imageUrl,
      BigDecimal price,
      String currency,
      boolean inStock,
      boolean hasModifiers) {}

  // A dish is in the menu of a country when it is visible, its category is visible and it has a
  // regional_price row for that country. hasModifiers uses the same rule as the product modal
  // (catalog.detail): a visible modifier with a price in this country.
  private static final String PRODUCTS_SQL =
      """
      SELECT p.id, p.category_id, p.name, p.description, p.image_url,
             rp.price, c.currency_code, rp.in_stock,
             EXISTS (
                 SELECT 1
                 FROM modifier m
                 JOIN modifier_price mp ON mp.modifier_id = m.id
                 WHERE m.product_id = p.id AND mp.country_id = c.id AND m.visible
             ) AS has_modifiers
      FROM product p
      JOIN category cat ON cat.id = p.category_id
      JOIN regional_price rp ON rp.product_id = p.id
      JOIN country c ON c.id = rp.country_id
      WHERE c.code = :country AND p.visible AND cat.visible
      """;

  private final JdbcClient jdbc;

  public CatalogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Visible categories with at least one visible dish priced in this country. */
  public List<CategoryRow> findCategories(String countryCode) {
    return jdbc.sql(
            """
            SELECT cat.id, cat.name
            FROM category cat
            WHERE cat.visible
              AND EXISTS (
                  SELECT 1
                  FROM product p
                  JOIN regional_price rp ON rp.product_id = p.id
                  JOIN country c ON c.id = rp.country_id
                  WHERE p.category_id = cat.id AND p.visible AND c.code = :country)
            ORDER BY cat.sort_order, cat.id
            """)
        .param("country", countryCode)
        .query((rs, i) -> new CategoryRow(rs.getLong("id"), rs.getString("name")))
        .list();
  }

  /** Dishes priced in this country, from all categories or one. Order: category, then id. */
  public List<ProductRow> findProducts(String countryCode, Long categoryId) {
    String sql = PRODUCTS_SQL;
    if (categoryId != null) {
      sql += "AND p.category_id = :categoryId\n";
    }
    sql += "ORDER BY cat.sort_order, p.id";

    JdbcClient.StatementSpec statement = jdbc.sql(sql).param("country", countryCode);
    if (categoryId != null) {
      statement = statement.param("categoryId", categoryId);
    }
    return statement.query(CatalogRepository::toProductRow).list();
  }

  private static ProductRow toProductRow(ResultSet rs, int rowNum) throws SQLException {
    return new ProductRow(
        rs.getLong("id"),
        rs.getLong("category_id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("image_url"),
        rs.getBigDecimal("price"),
        rs.getString("currency_code").trim(),
        rs.getBoolean("in_stock"),
        rs.getBoolean("has_modifiers"));
  }
}
