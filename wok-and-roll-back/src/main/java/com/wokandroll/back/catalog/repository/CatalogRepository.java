package com.wokandroll.back.catalog.repository;

import com.wokandroll.back.catalog.dto.SetItemResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
      Integer pieces,
      Integer weightGrams,
      Integer volumeMl,
      BigDecimal price,
      String currency,
      boolean inStock,
      boolean hasModifiers,
      BigDecimal regularPrice) {}

  /** The name and the ingredients of a dish, in English, for what it has (DishFacts). */
  public record DishText(String name, String description) {}

  /**
   * The regular price of the set {@code p} in the country {@code c}: quantity x regional price of
   * each of its dishes. NULL for a dish (no set items) and when a dish of the set has no price in
   * the country, so a saving is never shown from a part of the set. Shared with the product modal.
   */
  public static final String REGULAR_PRICE_SQL =
      """
      (SELECT CASE WHEN count(*) > 0 AND count(sp.price) = count(*)
                   THEN sum(sp.price * si.quantity) END
       FROM product_set_item si
       LEFT JOIN regional_price sp ON sp.product_id = si.product_id AND sp.country_id = c.id
       WHERE si.set_id = p.id) AS regular_price
      """;

  /** Dishes of every set (or of one), in their order. Shared with the product modal. */
  public static final String SET_ITEMS_SQL =
      """
      SELECT si.set_id, si.product_id, d.name, si.quantity
      FROM product_set_item si
      JOIN product d ON d.id = si.product_id
      """;

  // A dish is in the menu of a country when it is visible, its category is visible and it has a
  // regional_price row for that country. hasModifiers uses the same rule as the product modal
  // (catalog.detail): a visible modifier with a price in this country.
  private static final String PRODUCTS_SQL =
      """
      SELECT p.id, p.category_id, p.name, p.description, p.image_url,
             p.pieces, p.weight_grams, p.volume_ml, rp.price, c.currency_code, rp.in_stock,
             EXISTS (
                 SELECT 1
                 FROM modifier m
                 JOIN modifier_price mp ON mp.modifier_id = m.id
                 WHERE m.product_id = p.id AND mp.country_id = c.id AND m.visible
             ) AS has_modifiers,
      """
          + REGULAR_PRICE_SQL
          + """
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

  /** The dishes of every set by the id of the set; a set is small and there are few of them. */
  public Map<Long, List<SetItemResponse>> findSetItems() {
    return jdbc
        .sql(SET_ITEMS_SQL + "ORDER BY si.set_id, si.sort_order")
        .query((rs, i) -> Map.entry(rs.getLong("set_id"), setItem(rs)))
        .list()
        .stream()
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
  }

  /** The name and ingredients of every dish (a small table), so a set is read from its dishes. */
  public Map<Long, DishText> findDishTexts() {
    return findDishTexts(jdbc);
  }

  /** Shared with the product modal. */
  public static Map<Long, DishText> findDishTexts(JdbcClient jdbc) {
    return jdbc
        .sql("SELECT id, name, description FROM product")
        .query(
            (rs, i) ->
                Map.entry(
                    rs.getLong("id"),
                    new DishText(rs.getString("name"), rs.getString("description"))))
        .list()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** One dish of a set from a row of {@link #SET_ITEMS_SQL}. */
  public static SetItemResponse setItem(ResultSet rs) throws SQLException {
    return new SetItemResponse(
        rs.getLong("product_id"), rs.getString("name"), rs.getInt("quantity"));
  }

  private static ProductRow toProductRow(ResultSet rs, int rowNum) throws SQLException {
    return new ProductRow(
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
        rs.getBoolean("in_stock"),
        rs.getBoolean("has_modifiers"),
        rs.getBigDecimal("regular_price"));
  }
}
