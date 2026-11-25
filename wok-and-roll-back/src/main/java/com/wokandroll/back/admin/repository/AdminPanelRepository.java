package com.wokandroll.back.admin.repository;

import com.wokandroll.back.admin.dto.AdminPanelDtos.Account;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminBookingSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminCafeResponse;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminOrderSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.AdminUserSummary;
import com.wokandroll.back.admin.dto.AdminPanelDtos.BookingCounts;
import com.wokandroll.back.admin.dto.AdminPanelDtos.DayOrders;
import com.wokandroll.back.admin.dto.AdminPanelDtos.Named;
import com.wokandroll.back.admin.dto.AdminPanelDtos.TopDish;
import com.wokandroll.back.booking.service.BookingRules;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL of the admin panel API (api-contract.md section 3.20). Every filter is a named parameter that
 * may be null: {@code cast(:x AS …) IS NULL OR …} keeps one fixed statement per list, so no SQL is
 * ever built from strings. The search uses {@code position()}, so % and _ in it are plain text.
 */
@Repository
public class AdminPanelRepository {

  private final JdbcClient jdbc;

  public AdminPanelRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The filters of the order and booking lists; every field may be null. */
  public record ListFilter(
      String country, Long cafeId, String status, LocalDate from, LocalDate to, String query) {}

  // ---- overview ------------------------------------------------------------------------------

  /** Orders of the period by status: all four, zeros too, in the order of their life. */
  public Map<String, Long> ordersByStatus(LocalDate from, LocalDate to) {
    Map<String, Long> counts = new LinkedHashMap<>();
    jdbc.sql(
            """
            SELECT s.status, count(o.id) AS orders
            FROM unnest(ARRAY['NEW', 'CONFIRMED', 'DONE', 'CANCELLED']) WITH ORDINALITY
                 AS s (status, pos)
            LEFT JOIN customer_order o
                   ON o.status = s.status AND o.order_date BETWEEN :from AND :to
            GROUP BY s.status, s.pos
            ORDER BY s.pos
            """)
        .param("from", from)
        .param("to", to)
        .query((rs, i) -> Map.entry(rs.getString("status"), rs.getLong("orders")))
        .list()
        .forEach(e -> counts.put(e.getKey(), e.getValue()));
    return counts;
  }

  /** Orders of the period, cancelled too, by way of getting them. */
  public record ScenarioCounts(long pickup, long delivery) {}

  public ScenarioCounts ordersByScenario(LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT count(*) FILTER (WHERE scenario = 'PICKUP')   AS pickup,
                   count(*) FILTER (WHERE scenario = 'DELIVERY') AS delivery
            FROM customer_order WHERE order_date BETWEEN :from AND :to
            """)
        .param("from", from)
        .param("to", to)
        .query((rs, i) -> new ScenarioCounts(rs.getLong("pickup"), rs.getLong("delivery")))
        .single();
  }

  /** Bookings of the period: all, cancelled, and the guests of the confirmed ones. */
  public BookingCounts bookingCounts(LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
                   coalesce(sum(guests) FILTER (WHERE status = 'CONFIRMED'), 0) AS guests
            FROM booking WHERE booking_date BETWEEN :from AND :to
            """)
        .param("from", from)
        .param("to", to)
        .query(
            (rs, i) ->
                new BookingCounts(
                    rs.getLong("total"), rs.getLong("cancelled"), rs.getLong("guests")))
        .single();
  }

  /** Accounts by role (all four, zeros too). */
  public Map<String, Long> accountsByRole() {
    Map<String, Long> counts = new LinkedHashMap<>();
    jdbc.sql(
            """
            SELECT r.code, count(u.id) AS accounts
            FROM role r LEFT JOIN app_user u ON u.role_id = r.id
            GROUP BY r.id, r.code
            ORDER BY r.id
            """)
        .query((rs, i) -> Map.entry(rs.getString("code").trim(), rs.getLong("accounts")))
        .list()
        .forEach(e -> counts.put(e.getKey(), e.getValue()));
    return counts;
  }

  public long newAccounts(LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT count(*) FROM app_user
            WHERE (created_at AT TIME ZONE 'UTC')::date BETWEEN :from AND :to
            """)
        .param("from", from)
        .param("to", to)
        .query(Long.class)
        .single();
  }

  /** Orders a day, all countries, without cancelled ones; every day of the period, zeros too. */
  public List<DayOrders> ordersByDay(LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT d::date AS day, count(o.id) AS orders
            FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS d
            LEFT JOIN customer_order o
                   ON o.order_date = d::date AND o.status <> 'CANCELLED'
            GROUP BY d
            ORDER BY d
            """)
        .param("from", from)
        .param("to", to)
        .query((rs, i) -> new DayOrders(rs.getObject("day", LocalDate.class), rs.getLong("orders")))
        .list();
  }

  /** The dishes ordered most in the period by quantity, without cancelled orders. */
  public List<TopDish> topDishes(LocalDate from, LocalDate to, int limit) {
    return jdbc.sql(
            """
            SELECT p.id, p.name, sum(i.quantity) AS quantity
            FROM order_item i
            JOIN customer_order o ON o.id = i.order_id
            JOIN product p ON p.id = i.product_id
            WHERE o.order_date BETWEEN :from AND :to AND o.status <> 'CANCELLED'
            GROUP BY p.id, p.name
            ORDER BY quantity DESC, p.id
            LIMIT :limit
            """)
        .param("from", from)
        .param("to", to)
        .param("limit", limit)
        .query(
            (rs, i) -> new TopDish(rs.getLong("id"), rs.getString("name"), rs.getLong("quantity")))
        .list();
  }

  // ---- orders --------------------------------------------------------------------------------

  // Newest day first, then the latest slot, then id, so a page never changes its order.
  private static final String ORDERS_SQL =
      """
      SELECT o.order_number, o.created_at, o.order_date, o.slot_start, o.slot_end,
             c.code AS country, o.currency_code, cf.id AS cafe_id, cf.name AS cafe_name,
             o.scenario, o.status, o.contact_name, o.contact_phone, o.total,
             u.email AS account_email, u.full_name AS account_name,
             (SELECT coalesce(sum(i.quantity), 0) FROM order_item i WHERE i.order_id = o.id)
               AS item_count
      FROM customer_order o
      JOIN country c ON c.id = o.country_id
      JOIN cafe cf ON cf.id = o.cafe_id
      LEFT JOIN app_user u ON u.id = o.user_id
      WHERE (CAST(:country AS varchar) IS NULL OR c.code = CAST(:country AS varchar))
        AND (CAST(:cafeId AS bigint) IS NULL OR o.cafe_id = CAST(:cafeId AS bigint))
        AND (CAST(:status AS varchar) IS NULL OR o.status = CAST(:status AS varchar))
        AND (CAST(:from AS date) IS NULL OR o.order_date >= CAST(:from AS date))
        AND (CAST(:to AS date) IS NULL OR o.order_date <= CAST(:to AS date))
        AND (CAST(:query AS varchar) IS NULL
             OR position(lower(CAST(:query AS varchar)) IN lower(
                  o.order_number || ' ' || o.contact_name || ' ' || o.contact_phone || ' '
                  || coalesce(o.contact_email, '') || ' ' || coalesce(u.email, ''))) > 0)
      ORDER BY o.order_date DESC, o.slot_start DESC, o.id DESC
      LIMIT :limit OFFSET :offset
      """;

  public List<AdminOrderSummary> findOrders(ListFilter filter, int limit, int offset) {
    return bind(jdbc.sql(ORDERS_SQL), filter)
        .param("limit", limit)
        .param("offset", offset)
        .query(AdminPanelRepository::toOrder)
        .list();
  }

  /** What the order body of section 3.9 does not have: email, comment, account, last change. */
  public record OrderExtras(
      String contactEmail, String comment, Account account, Instant updatedAt) {}

  public Optional<OrderExtras> findOrderExtras(String orderNumber) {
    return jdbc.sql(
            """
            SELECT o.contact_email, o.comment, o.updated_at,
                   u.email AS account_email, u.full_name AS account_name
            FROM customer_order o LEFT JOIN app_user u ON u.id = o.user_id
            WHERE o.order_number = :number
            """)
        .param("number", orderNumber)
        .query(
            (rs, i) ->
                new OrderExtras(
                    rs.getString("contact_email"),
                    rs.getString("comment"),
                    account(rs),
                    instant(rs, "updated_at")))
        .optional();
  }

  // ---- bookings ------------------------------------------------------------------------------

  private static final String BOOKINGS_SQL =
      """
      SELECT b.booking_number, b.created_at, b.booking_date, b.booking_time, c.code AS country,
             cf.id AS cafe_id, cf.name AS cafe_name, b.guests, t.seats, b.status,
             b.contact_name, b.contact_phone, b.contact_email, b.comment,
             u.email AS account_email, u.full_name AS account_name
      FROM booking b
      JOIN cafe cf ON cf.id = b.cafe_id
      JOIN country c ON c.id = cf.country_id
      JOIN cafe_table t ON t.id = b.table_id
      LEFT JOIN app_user u ON u.id = b.user_id
      WHERE (CAST(:country AS varchar) IS NULL OR c.code = CAST(:country AS varchar))
        AND (CAST(:cafeId AS bigint) IS NULL OR b.cafe_id = CAST(:cafeId AS bigint))
        AND (CAST(:status AS varchar) IS NULL OR b.status = CAST(:status AS varchar))
        AND (CAST(:from AS date) IS NULL OR b.booking_date >= CAST(:from AS date))
        AND (CAST(:to AS date) IS NULL OR b.booking_date <= CAST(:to AS date))
        AND (CAST(:query AS varchar) IS NULL
             OR position(lower(CAST(:query AS varchar)) IN lower(
                  b.booking_number || ' ' || b.contact_name || ' ' || b.contact_phone || ' '
                  || coalesce(b.contact_email, '') || ' ' || coalesce(u.email, ''))) > 0)
      ORDER BY b.booking_date DESC, b.booking_time DESC, b.id DESC
      LIMIT :limit OFFSET :offset
      """;

  public List<AdminBookingSummary> findBookings(ListFilter filter, int limit, int offset) {
    return bind(jdbc.sql(BOOKINGS_SQL), filter)
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) -> {
              LocalTime time = rs.getObject("booking_time", LocalTime.class);
              return new AdminBookingSummary(
                  rs.getString("booking_number"),
                  instant(rs, "created_at"),
                  rs.getObject("booking_date", LocalDate.class),
                  time,
                  time.plusMinutes(BookingRules.DURATION_MINUTES),
                  rs.getString("country").trim(),
                  new Named(rs.getLong("cafe_id"), rs.getString("cafe_name")),
                  rs.getInt("guests"),
                  rs.getInt("seats"),
                  rs.getString("status"),
                  rs.getString("contact_name"),
                  rs.getString("contact_phone"),
                  rs.getString("contact_email"),
                  rs.getString("comment"),
                  account(rs));
            })
        .list();
  }

  // ---- users ---------------------------------------------------------------------------------

  // Newest account first, then id.
  public List<AdminUserSummary> findUsers(String role, String query, int limit, int offset) {
    return jdbc.sql(
            """
            SELECT u.id, u.email, u.full_name, u.phone, r.code AS role, u.created_at,
                   count(o.id) AS orders, max(o.created_at) AS last_order_at
            FROM app_user u
            JOIN role r ON r.id = u.role_id
            LEFT JOIN customer_order o ON o.user_id = u.id
            WHERE (CAST(:role AS varchar) IS NULL OR r.code = CAST(:role AS varchar))
              AND (CAST(:query AS varchar) IS NULL
                   OR position(lower(CAST(:query AS varchar)) IN lower(
                        u.email || ' ' || u.full_name || ' ' || coalesce(u.phone, ''))) > 0)
            GROUP BY u.id, u.email, u.full_name, u.phone, r.code, u.created_at
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT :limit OFFSET :offset
            """)
        .param("role", role)
        .param("query", query)
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) ->
                new AdminUserSummary(
                    rs.getLong("id"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("role").trim(),
                    instant(rs, "created_at"),
                    rs.getLong("orders"),
                    instant(rs, "last_order_at")))
        .list();
  }

  // ---- cafés ---------------------------------------------------------------------------------

  /** Every café, closed ones too, in the order of the countries; "today" in its time zone. */
  public List<AdminCafeResponse> findCafes() {
    return jdbc.sql(
            """
            WITH local AS (
                SELECT cf.*, c.code AS country, c.time_zone,
                       (now() AT TIME ZONE c.time_zone)::date AS today
                FROM cafe cf JOIN country c ON c.id = cf.country_id
            )
            SELECT l.id, l.country, l.city, l.name, l.address, l.phone, l.opens_at, l.closes_at,
                   l.time_zone, l.active, l.slot_capacity, l.today,
                   (SELECT count(*) FROM cafe_table t WHERE t.cafe_id = l.id) AS tables,
                   (SELECT coalesce(sum(t.seats), 0) FROM cafe_table t WHERE t.cafe_id = l.id)
                     AS seats,
                   (SELECT count(*) FROM customer_order o
                    WHERE o.cafe_id = l.id AND o.order_date = l.today AND o.status <> 'CANCELLED')
                     AS today_orders,
                   (SELECT count(*) FROM booking b
                    WHERE b.cafe_id = l.id AND b.booking_date = l.today
                      AND b.status = 'CONFIRMED') AS today_bookings
            FROM local l
            ORDER BY l.country_id, l.id
            """)
        .query(
            (rs, i) ->
                new AdminCafeResponse(
                    rs.getLong("id"),
                    rs.getString("country").trim(),
                    rs.getString("city"),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("phone"),
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getString("time_zone"),
                    rs.getBoolean("active"),
                    rs.getInt("tables"),
                    rs.getInt("seats"),
                    rs.getInt("slot_capacity"),
                    rs.getObject("today", LocalDate.class),
                    rs.getLong("today_orders"),
                    rs.getLong("today_bookings")))
        .list();
  }

  // ---- helpers -------------------------------------------------------------------------------

  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec sql, ListFilter filter) {
    return sql.param("country", filter.country())
        .param("cafeId", filter.cafeId())
        .param("status", filter.status())
        .param("from", filter.from())
        .param("to", filter.to())
        .param("query", filter.query());
  }

  private static AdminOrderSummary toOrder(ResultSet rs, int rowNum) throws SQLException {
    return new AdminOrderSummary(
        rs.getString("order_number"),
        instant(rs, "created_at"),
        rs.getObject("order_date", LocalDate.class),
        rs.getObject("slot_start", LocalTime.class),
        rs.getObject("slot_end", LocalTime.class),
        rs.getString("country").trim(),
        rs.getString("currency_code").trim(),
        new Named(rs.getLong("cafe_id"), rs.getString("cafe_name")),
        rs.getString("scenario"),
        rs.getString("status"),
        rs.getString("contact_name"),
        rs.getString("contact_phone"),
        account(rs),
        rs.getInt("item_count"),
        rs.getBigDecimal("total"));
  }

  private static Account account(ResultSet rs) throws SQLException {
    String email = rs.getString("account_email");
    return email == null ? null : new Account(email, rs.getString("account_name"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
