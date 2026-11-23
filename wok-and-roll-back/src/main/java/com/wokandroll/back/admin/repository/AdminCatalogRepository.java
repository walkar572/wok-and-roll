package com.wokandroll.back.admin.repository;

import com.wokandroll.back.admin.dto.AdminDtos.AdminProductResponse;
import com.wokandroll.back.admin.dto.AdminDtos.CategoryResponse;
import com.wokandroll.back.admin.dto.AdminDtos.PriceResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL of the admin catalog (#178). Every update checks the version (optimistic locking): zero
 * updated rows means somebody else changed the row first.
 */
@Repository
public class AdminCatalogRepository {

  // All products, hidden ones too, with the price of one country (null when not sold there).
  private static final String PRODUCTS_SQL =
      """
      SELECT p.id, p.category_id, cat.name AS category_name, p.name, p.description, p.image_url,
             p.visible, p.version, c.code AS country, c.currency_code, rp.price, rp.in_stock,
             rp.version AS price_version
      FROM product p
      JOIN category cat ON cat.id = p.category_id
      JOIN country c ON c.code = :country
      LEFT JOIN regional_price rp ON rp.product_id = p.id AND rp.country_id = c.id
      """;

  private final JdbcClient jdbc;

  public AdminCatalogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<AdminProductResponse> findProducts(String country, int limit, int offset) {
    return jdbc.sql(
            PRODUCTS_SQL + " ORDER BY cat.sort_order, cat.id, p.id LIMIT :limit OFFSET :offset")
        .param("country", country)
        .param("limit", limit)
        .param("offset", offset)
        .query(AdminCatalogRepository::toProduct)
        .list();
  }

  public Optional<AdminProductResponse> findProduct(long id, String country) {
    return jdbc.sql(PRODUCTS_SQL + " WHERE p.id = :id")
        .param("country", country)
        .param("id", id)
        .query(AdminCatalogRepository::toProduct)
        .optional();
  }

  public boolean productExists(long id) {
    return count("SELECT count(*) FROM product WHERE id = :id", id) > 0;
  }

  public boolean categoryExists(long id) {
    return count("SELECT count(*) FROM category WHERE id = :id", id) > 0;
  }

  public boolean categoryNameTaken(String name, Long exceptId) {
    return jdbc.sql(
                "SELECT count(*) FROM category WHERE lower(name) = lower(:name)"
                    + " AND (CAST(:id AS bigint) IS NULL OR id <> CAST(:id AS bigint))")
            .param("name", name)
            .param("id", exceptId)
            .query(Long.class)
            .single()
        > 0;
  }

  public long insertProduct(
      long categoryId, String name, String description, String imageUrl, boolean visible) {
    return jdbc.sql(
            """
            INSERT INTO product (category_id, name, description, image_url, visible)
            VALUES (:categoryId, :name, :description, :imageUrl, :visible)
            RETURNING id
            """)
        .param("categoryId", categoryId)
        .param("name", name)
        .param("description", description)
        .param("imageUrl", imageUrl)
        .param("visible", visible)
        .query(Long.class)
        .single();
  }

  /**
   * Changes only the sent fields. A {@code set*} flag says whether the field was sent, so a
   * description can also be removed. Returns false when the version did not match.
   */
  public boolean updateProduct(
      long id,
      int version,
      Long categoryId,
      String name,
      boolean setDescription,
      String description,
      boolean setImageUrl,
      String imageUrl,
      Boolean visible) {
    return jdbc.sql(
                """
                UPDATE product SET
                  category_id = COALESCE(CAST(:categoryId AS bigint), category_id),
                  name = COALESCE(CAST(:name AS varchar), name),
                  description = CASE WHEN :setDescription THEN CAST(:description AS varchar)
                                     ELSE description END,
                  image_url = CASE WHEN :setImageUrl THEN CAST(:imageUrl AS varchar)
                                   ELSE image_url END,
                  visible = COALESCE(CAST(:visible AS boolean), visible),
                  version = version + 1
                WHERE id = :id AND version = :version
                """)
            .param("id", id)
            .param("version", version)
            .param("categoryId", categoryId)
            .param("name", name)
            .param("setDescription", setDescription)
            .param("description", description)
            .param("setImageUrl", setImageUrl)
            .param("imageUrl", imageUrl)
            .param("visible", visible)
            .update()
        == 1;
  }

  /** The price row of a product in a country, if it exists: its version. */
  public Optional<Integer> priceVersion(long productId, String country) {
    return jdbc.sql(
            """
            SELECT rp.version FROM regional_price rp
            JOIN country c ON c.id = rp.country_id
            WHERE rp.product_id = :productId AND c.code = :country
            """)
        .param("productId", productId)
        .param("country", country)
        .query(Integer.class)
        .optional();
  }

  /** Creates the price; returns false when another admin created it at the same moment. */
  public boolean insertPrice(long productId, String country, BigDecimal price, boolean inStock) {
    return jdbc.sql(
                """
                INSERT INTO regional_price (product_id, country_id, price, in_stock)
                SELECT :productId, c.id, :price, :inStock FROM country c WHERE c.code = :country
                ON CONFLICT (product_id, country_id) DO NOTHING
                """)
            .param("productId", productId)
            .param("country", country)
            .param("price", price)
            .param("inStock", inStock)
            .update()
        == 1;
  }

  /** Changes the price; returns false when the version did not match. */
  public boolean updatePrice(
      long productId, String country, BigDecimal price, boolean inStock, int version) {
    return jdbc.sql(
                """
                UPDATE regional_price rp
                SET price = :price, in_stock = :inStock, version = rp.version + 1
                FROM country c
                WHERE c.id = rp.country_id AND c.code = :country
                  AND rp.product_id = :productId AND rp.version = :version
                """)
            .param("productId", productId)
            .param("country", country)
            .param("price", price)
            .param("inStock", inStock)
            .param("version", version)
            .update()
        == 1;
  }

  public Optional<PriceResponse> findPrice(long productId, String country) {
    return jdbc.sql(
            """
            SELECT rp.product_id, c.code, rp.price, c.currency_code, rp.in_stock, rp.version
            FROM regional_price rp
            JOIN country c ON c.id = rp.country_id
            WHERE rp.product_id = :productId AND c.code = :country
            """)
        .param("productId", productId)
        .param("country", country)
        .query(
            (rs, i) ->
                new PriceResponse(
                    rs.getLong("product_id"),
                    rs.getString("code").trim(),
                    rs.getBigDecimal("price"),
                    rs.getString("currency_code").trim(),
                    rs.getBoolean("in_stock"),
                    rs.getInt("version")))
        .optional();
  }

  public List<CategoryResponse> findCategories() {
    return jdbc.sql(
            "SELECT id, name, sort_order, visible, version FROM category ORDER BY sort_order, id")
        .query(AdminCatalogRepository::toCategory)
        .list();
  }

  public Optional<CategoryResponse> findCategory(long id) {
    return jdbc.sql("SELECT id, name, sort_order, visible, version FROM category WHERE id = :id")
        .param("id", id)
        .query(AdminCatalogRepository::toCategory)
        .optional();
  }

  public long insertCategory(String name, int sortOrder, boolean visible) {
    return jdbc.sql(
            """
            INSERT INTO category (name, sort_order, visible) VALUES (:name, :sortOrder, :visible)
            RETURNING id
            """)
        .param("name", name)
        .param("sortOrder", sortOrder)
        .param("visible", visible)
        .query(Long.class)
        .single();
  }

  public boolean updateCategory(
      long id, int version, String name, Integer sortOrder, Boolean visible) {
    return jdbc.sql(
                """
                UPDATE category SET
                  name = COALESCE(CAST(:name AS varchar), name),
                  sort_order = COALESCE(CAST(:sortOrder AS int), sort_order),
                  visible = COALESCE(CAST(:visible AS boolean), visible),
                  version = version + 1
                WHERE id = :id AND version = :version
                """)
            .param("id", id)
            .param("version", version)
            .param("name", name)
            .param("sortOrder", sortOrder)
            .param("visible", visible)
            .update()
        == 1;
  }

  private long count(String sql, long id) {
    return jdbc.sql(sql).param("id", id).query(Long.class).single();
  }

  private static AdminProductResponse toProduct(ResultSet rs, int rowNum) throws SQLException {
    BigDecimal price = rs.getBigDecimal("price");
    boolean sold = price != null;
    return new AdminProductResponse(
        rs.getLong("id"),
        rs.getLong("category_id"),
        rs.getString("category_name"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("image_url"),
        rs.getBoolean("visible"),
        rs.getInt("version"),
        rs.getString("country").trim(),
        price,
        rs.getString("currency_code").trim(),
        sold ? rs.getBoolean("in_stock") : null,
        sold ? rs.getInt("price_version") : null);
  }

  private static CategoryResponse toCategory(ResultSet rs, int rowNum) throws SQLException {
    return new CategoryResponse(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getInt("sort_order"),
        rs.getBoolean("visible"),
        rs.getInt("version"));
  }
}
