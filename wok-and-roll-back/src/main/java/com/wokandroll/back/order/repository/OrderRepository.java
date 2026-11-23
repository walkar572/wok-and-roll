package com.wokandroll.back.order.repository;

import com.wokandroll.back.cart.dto.CartLineResponse;
import com.wokandroll.back.cart.dto.CartModifierResponse;
import com.wokandroll.back.order.dto.OrderResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL of the order (#149): the café lookup, the insert of the order with its lines, and the
 * read of a saved order as the response of the API. Create and replay both read the order from the
 * database, so a replay returns exactly the body of the first answer.
 */
@Repository
public class OrderRepository {

  // A café can take orders when it is active and belongs to the country.
  private static final String CAFE_SQL =
      """
      SELECT cf.id, cf.name, cf.address, cf.opens_at, cf.closes_at, cf.slot_capacity
      FROM cafe cf
      JOIN country c ON c.id = cf.country_id
      WHERE c.code = :country AND cf.id = :cafeId AND cf.active
      """;

  // ON CONFLICT DO NOTHING without a target covers both unique keys (order_number and
  // idempotency_key): the caller finds out which one it was. A null key is allowed (no header).
  private static final String INSERT_ORDER_SQL =
      """
      INSERT INTO customer_order (
        order_number, user_id, country_id, order_date, scenario, cafe_id, delivery_zone_id,
        slot_start, slot_end, delivery_address, contact_name, contact_phone, contact_email,
        comment, currency_code, subtotal, vat_rate, vat_amount, amount_incl_vat, discount_rate,
        discount_amount, delivery_fee, total, idempotency_key, request_hash)
      VALUES (
        :orderNumber, :userId, (SELECT id FROM country WHERE code = :country), :orderDate,
        :scenario, :cafeId, :zoneId, :slotStart, :slotEnd, :address, :contactName, :contactPhone,
        :contactEmail, :comment, :currency, :subtotal, :vatRate, :vat, :amountInclVat,
        :discountRate, :discount, :deliveryFee, :total, CAST(:key AS uuid), :hash)
      ON CONFLICT DO NOTHING
      RETURNING id
      """;

  private static final String ORDER_BY_NUMBER_SQL =
      """
      SELECT o.id, o.order_number, o.status, o.created_at, c.code AS country, o.currency_code,
             o.order_date, o.slot_start, o.slot_end, o.scenario, cf.id AS cafe_id,
             cf.name AS cafe_name, cf.address AS cafe_address, z.id AS zone_id,
             z.name AS zone_name, o.delivery_address, o.contact_name, o.contact_phone,
             o.subtotal, o.vat_rate, o.vat_amount, o.amount_incl_vat, o.discount_rate,
             o.discount_amount, o.delivery_fee, o.total
      FROM customer_order o
      JOIN country c ON c.id = o.country_id
      JOIN cafe cf ON cf.id = o.cafe_id
      LEFT JOIN delivery_zone z ON z.id = o.delivery_zone_id
      WHERE o.order_number = :number
      """;

  private final JdbcClient jdbc;

  public OrderRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The active café of the country, or empty (UNKNOWN_CAFE). */
  public Optional<CafeRow> findActiveCafe(String countryCode, long cafeId) {
    return jdbc.sql(CAFE_SQL)
        .param("country", countryCode)
        .param("cafeId", cafeId)
        .query(
            (rs, i) ->
                new CafeRow(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getObject("opens_at", java.time.LocalTime.class),
                    rs.getObject("closes_at", java.time.LocalTime.class),
                    rs.getInt("slot_capacity")))
        .optional();
  }

  /** A saved order with this idempotency key: its number and the hash of its request. */
  public Optional<SavedOrder> findByKey(UUID key) {
    return jdbc.sql(
            "SELECT order_number, request_hash FROM customer_order WHERE idempotency_key ="
                + " CAST(:key AS uuid)")
        .param("key", key.toString())
        .query(
            (rs, i) -> new SavedOrder(rs.getString("order_number"), rs.getString("request_hash")))
        .optional();
  }

  /** The number and the request hash of a saved order. */
  public record SavedOrder(String orderNumber, String requestHash) {}

  /** Inserts the order row. Returns its id, or empty if the order number or the key is taken. */
  public Optional<Long> insertOrder(NewOrder o) {
    return jdbc.sql(INSERT_ORDER_SQL)
        .param("orderNumber", o.orderNumber())
        .param("userId", o.userId())
        .param("country", o.countryCode())
        .param("orderDate", o.orderDate())
        .param("scenario", o.scenario())
        .param("cafeId", o.cafeId())
        .param("zoneId", o.deliveryZoneId())
        .param("slotStart", o.slotStart())
        .param("slotEnd", o.slotEnd())
        .param("address", o.deliveryAddress())
        .param("contactName", o.contactName())
        .param("contactPhone", o.contactPhone())
        .param("contactEmail", o.contactEmail())
        .param("comment", o.comment())
        .param("currency", o.currencyCode())
        .param("subtotal", o.subtotal())
        .param("vatRate", o.vatRate())
        .param("vat", o.vat())
        .param("amountInclVat", o.amountInclVat())
        .param("discountRate", o.discountRate())
        .param("discount", o.discount())
        .param("deliveryFee", o.deliveryFee())
        .param("total", o.total())
        .param("key", o.idempotencyKey() == null ? null : o.idempotencyKey().toString())
        .param("hash", o.requestHash())
        .query(Long.class)
        .optional();
  }

  /** Inserts a line with the copied name and prices. Returns the id of the line. */
  public long insertItem(long orderId, CartLineResponse line) {
    return jdbc.sql(
            """
            INSERT INTO order_item (order_id, product_id, product_name, quantity, unit_price,
                                    line_total)
            VALUES (:orderId, :productId, :name, :quantity, :unitPrice, :lineTotal)
            RETURNING id
            """)
        .param("orderId", orderId)
        .param("productId", line.productId())
        .param("name", line.name())
        .param("quantity", line.quantity())
        .param("unitPrice", line.unitPrice())
        .param("lineTotal", line.lineTotal())
        .query(Long.class)
        .single();
  }

  /** Inserts a chosen modifier of a line with the copied name and price change. */
  public void insertModifier(long itemId, CartModifierResponse modifier) {
    jdbc.sql(
            """
            INSERT INTO order_item_modifier (order_item_id, modifier_id, modifier_name, price_delta)
            VALUES (:itemId, :modifierId, :name, :priceDelta)
            """)
        .param("itemId", itemId)
        .param("modifierId", modifier.id())
        .param("name", modifier.name())
        .param("priceDelta", modifier.priceDelta())
        .update();
  }

  /** The saved order as the API response, with its lines and modifiers. */
  public Optional<OrderResponse> findResponse(String orderNumber) {
    Optional<OrderHeader> header =
        jdbc.sql(ORDER_BY_NUMBER_SQL)
            .param("number", orderNumber)
            .query(OrderRepository::toHeader)
            .optional();
    return header.map(h -> h.toResponse(readLines(h.id)));
  }

  private List<CartLineResponse> readLines(long orderId) {
    Map<Long, List<CartModifierResponse>> modifiersByItem = new LinkedHashMap<>();
    // The same order as in the quote: the size first, then the extras, each by id.
    jdbc.sql(
            """
            SELECT m.order_item_id, m.modifier_id, m.modifier_name, m.price_delta, md.type
            FROM order_item_modifier m
            JOIN order_item i ON i.id = m.order_item_id
            JOIN modifier md ON md.id = m.modifier_id
            WHERE i.order_id = :orderId
            ORDER BY m.order_item_id, CASE md.type WHEN 'SIZE' THEN 0 ELSE 1 END, m.modifier_id
            """)
        .param("orderId", orderId)
        .query(
            (rs, i) ->
                Map.entry(
                    rs.getLong("order_item_id"),
                    new CartModifierResponse(
                        rs.getLong("modifier_id"),
                        rs.getString("modifier_name"),
                        rs.getString("type"),
                        rs.getBigDecimal("price_delta"))))
        .list()
        .forEach(
            e ->
                modifiersByItem
                    .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                    .add(e.getValue()));

    return jdbc.sql(
            """
            SELECT id, product_id, product_name, quantity, unit_price, line_total
            FROM order_item WHERE order_id = :orderId ORDER BY id
            """)
        .param("orderId", orderId)
        .query(
            (rs, i) ->
                new CartLineResponse(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("line_total"),
                    modifiersByItem.getOrDefault(rs.getLong("id"), List.of())))
        .list();
  }

  /** The order row before the lines are added. */
  private record OrderHeader(long id, OrderResponse base) {

    OrderResponse toResponse(List<CartLineResponse> lines) {
      return new OrderResponse(
          base.orderNumber(),
          base.status(),
          base.createdAt(),
          base.country(),
          base.currency(),
          base.orderDate(),
          base.slotStart(),
          base.slotEnd(),
          base.scenario(),
          base.cafe(),
          base.deliveryZone(),
          base.deliveryAddress(),
          base.contactName(),
          base.contactPhone(),
          lines,
          base.subtotal(),
          base.vatRate(),
          base.vat(),
          base.amountInclVat(),
          base.discountRate(),
          base.discount(),
          base.deliveryFee(),
          base.total());
    }
  }

  private static OrderHeader toHeader(ResultSet rs, int rowNum) throws SQLException {
    long zoneId = rs.getLong("zone_id");
    OrderResponse.ZoneSummary zone =
        rs.wasNull() ? null : new OrderResponse.ZoneSummary(zoneId, rs.getString("zone_name"));
    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
    OrderResponse base =
        new OrderResponse(
            rs.getString("order_number"),
            rs.getString("status"),
            createdAt.toInstant(),
            rs.getString("country").trim(),
            rs.getString("currency_code").trim(),
            rs.getObject("order_date", java.time.LocalDate.class),
            rs.getObject("slot_start", java.time.LocalTime.class),
            rs.getObject("slot_end", java.time.LocalTime.class),
            rs.getString("scenario"),
            new OrderResponse.CafeSummary(
                rs.getLong("cafe_id"), rs.getString("cafe_name"), rs.getString("cafe_address")),
            zone,
            rs.getString("delivery_address"),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            List.of(),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("vat_rate"),
            rs.getBigDecimal("vat_amount"),
            rs.getBigDecimal("amount_incl_vat"),
            rs.getBigDecimal("discount_rate"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("delivery_fee"),
            rs.getBigDecimal("total"));
    return new OrderHeader(rs.getLong("id"), base);
  }
}
