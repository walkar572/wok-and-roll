package com.wokandroll.back.order.service;

import com.wokandroll.back.cart.dto.CartItemRequest;
import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.common.time.CountryClock;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository;
import com.wokandroll.back.delivery.repository.DeliveryZoneRepository.DeliveryZoneRow;
import com.wokandroll.back.order.dto.OrderRequest;
import com.wokandroll.back.order.repository.CafeRow;
import com.wokandroll.back.order.repository.OrderRepository;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.PricingItem;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.service.PricingLimits;
import com.wokandroll.back.pricing.service.PricingService;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Checks an order request (#150, api-contract.md §3.9) and prices it. All problems are collected
 * and returned together in one 400; the code is the code of the first problem, and the field keys
 * are the names of the request fields (the checkout page and the QA tests rely on them).
 *
 * <p>The order of the checks is the order of the fields in the contract. The pure rules (formats,
 * lengths) need no database; the café, the zone and the pricing do.
 */
@Component
public class OrderValidator {

  static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  static final String UNKNOWN_COUNTRY = "UNKNOWN_COUNTRY";
  static final String UNKNOWN_CAFE = "UNKNOWN_CAFE";
  static final String UNKNOWN_DELIVERY_ZONE = "UNKNOWN_DELIVERY_ZONE";
  static final String PAST_DATE = "PAST_DATE";
  static final String INVALID_SLOT = "INVALID_SLOT";

  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}");
  private static final Pattern PHONE = Pattern.compile("\\+\\d{8,15}");
  // Simple on purpose: one @, no spaces, a dot in the domain.
  private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+");

  private final CountryClock clock;
  private final DeliveryZoneRepository zones;
  private final OrderRepository orders;
  private final PricingService pricing;

  public OrderValidator(
      CountryClock clock,
      DeliveryZoneRepository zones,
      OrderRepository orders,
      PricingService pricing) {
    this.clock = clock;
    this.zones = zones;
    this.orders = orders;
    this.pricing = pricing;
  }

  /** Returns the cleaned order with its price, or throws 400 with all the problems. */
  public ValidOrder validate(OrderRequest request) {
    Problems problems = new Problems();

    String country = checkCountry(request.country(), problems);
    LocalDate today = country == null ? null : clock.today(country);
    LocalDate date = checkDate(request.orderDate(), today, problems);
    Scenario scenario = checkScenario(request.scenario(), problems);

    CafeRow cafe = null;
    DeliveryZoneRow zone = null;
    String address = null;
    if (scenario == Scenario.PICKUP) {
      cafe = checkPickupCafe(request, country, problems);
      if (request.deliveryZoneId() != null) {
        problems.add(VALIDATION_ERROR, "deliveryZoneId", "must be empty for pickup");
      }
      if (request.deliveryAddress() != null && !request.deliveryAddress().isBlank()) {
        problems.add(VALIDATION_ERROR, "deliveryAddress", "must be empty for pickup");
      }
    } else if (scenario == Scenario.DELIVERY) {
      if (request.cafeId() != null) {
        problems.add(VALIDATION_ERROR, "cafeId", "must be empty for delivery");
      }
      zone = checkZone(request.deliveryZoneId(), country, problems);
      if (zone != null) {
        cafe = orders.findActiveCafe(country, zone.cafeId()).orElse(null);
      }
      address = checkText("deliveryAddress", request.deliveryAddress(), 5, 200, true, problems);
    }

    LocalTime slotStart = checkSlotFormat(request.slotStart(), problems);
    if (slotStart != null && cafe != null && date != null && country != null) {
      checkSlot(slotStart, date, today, cafe, country, problems);
    }

    String name = checkText("contactName", request.contactName(), 2, 100, true, problems);
    String phone = checkPhone(request.contactPhone(), problems);
    String email = checkEmail(request.contactEmail(), problems);
    String comment = checkText("comment", request.comment(), 0, 300, false, problems);
    if (request.expectedTotal() != null && request.expectedTotal().signum() < 0) {
      problems.add(VALIDATION_ERROR, "expectedTotal", "must be 0 or more");
    }

    List<PricingItem> items = checkItems(request.items(), problems);

    PriceBreakdown price = null;
    if (items != null && country != null && scenario != null) {
      price = priceCart(country, items, scenario, request.deliveryZoneId(), problems);
    }
    problems.throwIfAny();

    return new ValidOrder(
        country,
        date,
        scenario.name(),
        cafe,
        zone == null ? null : zone.id(),
        slotStart,
        slotStart.plusMinutes(OrderLimits.SLOT_MINUTES),
        address,
        name,
        phone,
        email,
        comment,
        price);
  }

  // ---- country, date, scenario -------------------------------------------------------------

  /** Returns the country code in upper case, or null if there is a problem. */
  private String checkCountry(String raw, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "country", "is required");
      return null;
    }
    String code = raw.trim().toUpperCase(Locale.ROOT);
    try {
      clock.zone(code);
      return code;
    } catch (IllegalArgumentException e) {
      problems.add(UNKNOWN_COUNTRY, "country", "Unknown country code");
      return null;
    }
  }

  private LocalDate checkDate(String raw, LocalDate today, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "orderDate", "is required");
      return null;
    }
    LocalDate date;
    try {
      if (!DATE.matcher(raw.trim()).matches()) {
        throw new DateTimeException("format");
      }
      date = LocalDate.parse(raw.trim());
    } catch (DateTimeException e) {
      problems.add(VALIDATION_ERROR, "orderDate", "must be a date in the format YYYY-MM-DD");
      return null;
    }
    if (today == null) {
      return date; // the country is wrong: the date cannot be compared, the country is reported
    }
    if (date.isBefore(today)) {
      problems.add(PAST_DATE, "orderDate", "must be today or later");
      return null;
    }
    if (date.isAfter(today.plusDays(OrderLimits.MAX_DAYS_AHEAD))) {
      problems.add(
          VALIDATION_ERROR,
          "orderDate",
          "must be within " + OrderLimits.MAX_DAYS_AHEAD + " days from today");
      return null;
    }
    return date;
  }

  private Scenario checkScenario(String raw, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "scenario", "is required");
      return null;
    }
    if (raw.equals("PICKUP") || raw.equals("DELIVERY")) {
      return Scenario.valueOf(raw);
    }
    problems.add(VALIDATION_ERROR, "scenario", "must be PICKUP or DELIVERY");
    return null;
  }

  // ---- café, zone, slot --------------------------------------------------------------------

  private CafeRow checkPickupCafe(OrderRequest request, String country, Problems problems) {
    if (request.cafeId() == null) {
      problems.add(VALIDATION_ERROR, "cafeId", "is required for pickup");
      return null;
    }
    if (country == null) {
      return null;
    }
    Optional<CafeRow> cafe = orders.findActiveCafe(country, request.cafeId());
    if (cafe.isEmpty()) {
      problems.add(
          UNKNOWN_CAFE, "cafeId", "Cafe " + request.cafeId() + " does not exist in " + country);
    }
    return cafe.orElse(null);
  }

  private DeliveryZoneRow checkZone(Long zoneId, String country, Problems problems) {
    if (zoneId == null) {
      problems.add(VALIDATION_ERROR, "deliveryZoneId", "is required for delivery");
      return null;
    }
    if (country == null) {
      return null;
    }
    Optional<DeliveryZoneRow> zone = zones.findActive(country, zoneId);
    if (zone.isEmpty()) {
      problems.add(
          UNKNOWN_DELIVERY_ZONE,
          "deliveryZoneId",
          "Delivery zone " + zoneId + " does not exist in " + country);
    }
    return zone.orElse(null);
  }

  private LocalTime checkSlotFormat(String raw, Problems problems) {
    if (raw == null || raw.isBlank()) {
      problems.add(VALIDATION_ERROR, "slotStart", "is required");
      return null;
    }
    try {
      if (!TIME.matcher(raw.trim()).matches()) {
        throw new DateTimeException("format");
      }
      return LocalTime.parse(raw.trim());
    } catch (DateTimeException e) {
      problems.add(VALIDATION_ERROR, "slotStart", "must be a time in the format HH:mm");
      return null;
    }
  }

  /**
   * A slot starts at opens_at and then every 30 minutes, the last start is 30 minutes before
   * closes_at. A slot that starts in less than 30 minutes (café time = country time) is closed.
   */
  private void checkSlot(
      LocalTime start,
      LocalDate date,
      LocalDate today,
      CafeRow cafe,
      String country,
      Problems problems) {
    long fromOpening = Duration.between(cafe.opensAt(), start).toMinutes();
    LocalTime lastStart = cafe.closesAt().minusMinutes(OrderLimits.SLOT_MINUTES);
    boolean onGrid =
        fromOpening >= 0
            && fromOpening % OrderLimits.SLOT_MINUTES == 0
            && !start.isAfter(lastStart);
    if (!onGrid) {
      problems.add(INVALID_SLOT, "slotStart", "is not a slot of this cafe on this day");
      return;
    }
    LocalDateTime now = clock.now(country);
    LocalDateTime earliest = now.plusMinutes(OrderLimits.MIN_LEAD_MINUTES);
    if (date.equals(today) && LocalDateTime.of(date, start).isBefore(earliest)) {
      problems.add(
          INVALID_SLOT,
          "slotStart",
          "starts in less than " + OrderLimits.MIN_LEAD_MINUTES + " minutes");
    }
  }

  // ---- contact -----------------------------------------------------------------------------

  /** Returns the trimmed text (null if empty and optional), or null after adding a problem. */
  private String checkText(
      String field, String raw, int min, int max, boolean required, Problems problems) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        problems.add(VALIDATION_ERROR, field, "is required");
      }
      return null;
    }
    if (text.length() < min || text.length() > max) {
      problems.add(
          VALIDATION_ERROR,
          field,
          min > 0
              ? "must be " + min + " to " + max + " characters"
              : "must be " + max + " characters or fewer");
      return null;
    }
    return text;
  }

  private String checkPhone(String raw, Problems problems) {
    String phone = raw == null ? "" : raw.trim();
    if (phone.isEmpty()) {
      problems.add(VALIDATION_ERROR, "contactPhone", "is required");
      return null;
    }
    if (!PHONE.matcher(phone).matches()) {
      problems.add(
          VALIDATION_ERROR,
          "contactPhone",
          "must be in the international format: + and 8 to 15 digits");
      return null;
    }
    return phone;
  }

  private String checkEmail(String raw, Problems problems) {
    String email = raw == null ? "" : raw.trim();
    if (email.isEmpty()) {
      return null;
    }
    if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
      problems.add(
          VALIDATION_ERROR, "contactEmail", "must be a valid email of 254 characters or fewer");
      return null;
    }
    return email.toLowerCase(Locale.ROOT);
  }

  // ---- items and price ---------------------------------------------------------------------

  /** The same rules as the quote. Returns the pricing items, or null if the lines are wrong. */
  private List<PricingItem> checkItems(List<CartItemRequest> items, Problems problems) {
    if (items == null || items.isEmpty()) {
      problems.add(VALIDATION_ERROR, "items", "must not be empty");
      return null;
    }
    if (items.size() > PricingLimits.MAX_LINES) {
      problems.add(
          VALIDATION_ERROR, "items", "must have " + PricingLimits.MAX_LINES + " lines or fewer");
      return null;
    }
    int before = problems.size();
    List<PricingItem> result = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      CartItemRequest item = items.get(i);
      String prefix = "items[" + i + "]";
      if (item == null) {
        problems.add(VALIDATION_ERROR, prefix, "must not be null");
        continue;
      }
      if (item.productId() == null) {
        problems.add(VALIDATION_ERROR, prefix + ".productId", "is required");
      }
      Integer quantity = checkQuantity(item.quantity(), prefix + ".quantity", problems);
      List<Long> modifiers = item.modifierIds() == null ? List.of() : item.modifierIds();
      for (int j = 0; j < modifiers.size(); j++) {
        if (modifiers.get(j) == null) {
          problems.add(VALIDATION_ERROR, prefix + ".modifierIds[" + j + "]", "must not be null");
        }
      }
      result.add(new PricingItem(item.productId(), quantity == null ? 0 : quantity, modifiers));
    }
    return problems.size() > before ? null : result;
  }

  private Integer checkQuantity(BigDecimal quantity, String field, Problems problems) {
    if (quantity == null) {
      problems.add(VALIDATION_ERROR, field, "is required");
      return null;
    }
    if (quantity.stripTrailingZeros().scale() > 0) {
      problems.add(VALIDATION_ERROR, field, "must be a whole number");
      return null;
    }
    if (quantity.compareTo(BigDecimal.ONE) < 0) {
      problems.add(VALIDATION_ERROR, field, "must be 1 or more");
      return null;
    }
    if (quantity.compareTo(BigDecimal.valueOf(PricingLimits.MAX_QUANTITY)) > 0) {
      problems.add(VALIDATION_ERROR, field, "must be " + PricingLimits.MAX_QUANTITY + " or less");
      return null;
    }
    return quantity.intValueExact();
  }

  /** Prices the cart; the problems of the pricing service (products, modifiers) are added. */
  private PriceBreakdown priceCart(
      String country, List<PricingItem> items, Scenario scenario, Long zoneId, Problems problems) {
    try {
      // The zone and the pickup rules are checked above; the fee is read by the pricing service.
      Long pricingZone = scenario == Scenario.DELIVERY ? zoneId : null;
      return pricing.calculate(new PricingRequest(country, items, scenario, pricingZone));
    } catch (ApiException e) {
      if (e.getStatus() != HttpStatus.BAD_REQUEST) {
        throw e; // for example PRICING_CONFIG_MISSING is a server problem, not the client's
      }
      Map<String, String> fields = e.getFields();
      if (fields.isEmpty()) {
        problems.add(e.getCode(), "items", e.getMessage());
      }
      fields.forEach((field, message) -> problems.add(e.getCode(), field, message));
      return null;
    }
  }

  /** Collects all problems. The first one decides the error code. */
  private static final class Problems {

    private final Map<String, String> fields = new LinkedHashMap<>();
    private String code;
    private int count;

    void add(String problemCode, String field, String message) {
      if (code == null) {
        code = problemCode;
      }
      fields.putIfAbsent(field, message);
      count++;
    }

    int size() {
      return count;
    }

    void throwIfAny() {
      if (code != null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, code, "Invalid order", fields);
      }
    }
  }
}
