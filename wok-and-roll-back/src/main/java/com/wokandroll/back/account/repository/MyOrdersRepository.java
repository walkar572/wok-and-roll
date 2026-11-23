package com.wokandroll.back.account.repository;

import com.wokandroll.back.account.dto.OrderSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL of "My orders" (#177). The user id always comes from the token, never from the request. */
@Repository
public class MyOrdersRepository {

  // Newest first, then id, so the order of a page never changes (index
  // ix_customer_order_user_created).
  private static final String SUMMARIES_SQL =
      """
      SELECT o.order_number, o.created_at, o.order_date, o.slot_start, c.code AS country,
             o.currency_code, o.scenario, o.status, o.total,
             (SELECT coalesce(sum(i.quantity), 0) FROM order_item i WHERE i.order_id = o.id)
               AS item_count
      FROM customer_order o
      JOIN country c ON c.id = o.country_id
      WHERE o.user_id = :userId
      ORDER BY o.created_at DESC, o.id DESC
      LIMIT :limit OFFSET :offset
      """;

  private final JdbcClient jdbc;

  public MyOrdersRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** At most {@code limit} orders of the user from {@code offset}. */
  public List<OrderSummaryResponse> findSummaries(long userId, int limit, int offset) {
    return jdbc.sql(SUMMARIES_SQL)
        .param("userId", userId)
        .param("limit", limit)
        .param("offset", offset)
        .query(MyOrdersRepository::toSummary)
        .list();
  }

  /**
   * The owner of an order: empty for an unknown number, empty Optional inside for a guest order.
   */
  public Optional<Optional<Long>> findOwner(String orderNumber) {
    return jdbc.sql("SELECT user_id FROM customer_order WHERE order_number = :number")
        .param("number", orderNumber)
        .query(
            (rs, i) -> {
              long id = rs.getLong("user_id");
              return rs.wasNull() ? Optional.<Long>empty() : Optional.of(id);
            })
        .optional();
  }

  private static OrderSummaryResponse toSummary(ResultSet rs, int rowNum) throws SQLException {
    return new OrderSummaryResponse(
        rs.getString("order_number"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("order_date", LocalDate.class),
        rs.getObject("slot_start", LocalTime.class),
        rs.getString("country").trim(),
        rs.getString("currency_code").trim(),
        rs.getString("scenario"),
        rs.getString("status"),
        rs.getInt("item_count"),
        rs.getBigDecimal("total"));
  }
}
