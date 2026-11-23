package com.wokandroll.back.pricing.service;

import com.wokandroll.back.common.error.ApiException;
import com.wokandroll.back.pricing.dto.DiscountTier;
import com.wokandroll.back.pricing.dto.ModifierType;
import com.wokandroll.back.pricing.dto.PriceBreakdown;
import com.wokandroll.back.pricing.dto.PricedLine;
import com.wokandroll.back.pricing.dto.PricedModifier;
import com.wokandroll.back.pricing.dto.PricingItem;
import com.wokandroll.back.pricing.dto.PricingRequest;
import com.wokandroll.back.pricing.dto.Scenario;
import com.wokandroll.back.pricing.repository.CountryPricing;
import com.wokandroll.back.pricing.repository.ModifierPrice;
import com.wokandroll.back.pricing.repository.PricingRepository;
import com.wokandroll.back.pricing.repository.ProductPrice;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The one place where a cart gets its prices. It reads regional prices, modifier deltas and the VAT
 * rate from the database, checks every line, and lets {@link PriceCalculator} do the math. The
 * client never sends prices.
 *
 * <p>All wrong lines are collected and returned together in one 400 error, so the UI can show every
 * problem at once. The error code is the code of the first problem.
 */
@Service
public class PricingService {

  static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  static final String UNKNOWN_COUNTRY = "UNKNOWN_COUNTRY";
  static final String UNKNOWN_PRODUCT = "UNKNOWN_PRODUCT";
  static final String PRODUCT_NOT_SOLD = "PRODUCT_NOT_SOLD";
  static final String OUT_OF_STOCK = "OUT_OF_STOCK";
  static final String INVALID_MODIFIER = "INVALID_MODIFIER";
  static final String PRICING_CONFIG_MISSING = "PRICING_CONFIG_MISSING";
  static final String UNKNOWN_DELIVERY_ZONE = "UNKNOWN_DELIVERY_ZONE";

  private final PricingRepository repository;
  private final PriceCalculator calculator;
  private final DeliveryZoneFees zoneFees;

  public PricingService(
      PricingRepository repository, PriceCalculator calculator, DeliveryZoneFees zoneFees) {
    this.repository = repository;
    this.calculator = calculator;
    this.zoneFees = zoneFees;
  }

  public PriceBreakdown calculate(PricingRequest request) {
    List<PricingItem> items = request.items();
    if (items == null || items.isEmpty()) {
      throw invalidCart("must not be empty");
    }
    if (items.size() > PricingLimits.MAX_LINES) {
      throw invalidCart("must have " + PricingLimits.MAX_LINES + " lines or fewer");
    }

    CountryPricing country = findCountry(request.countryCode());
    CartData data = loadData(country, items);

    Scenario scenario = request.scenario() == null ? Scenario.PICKUP : request.scenario();
    Problems problems = new Problems();
    // The zone problem goes first: it is about the whole order, so its code is the error code.
    BigDecimal deliveryFee =
        checkDelivery(scenario, request.deliveryZoneId(), country.code(), problems);
    List<PricedLine> lines = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      PricedLine line = priceItem(i, items.get(i), data, problems);
      if (line != null) {
        lines.add(line);
      }
    }
    problems.throwIfAny();

    BigDecimal subtotal = calculator.subtotal(lines.stream().map(PricedLine::lineTotal).toList());
    BigDecimal vatRate = calculator.round(country.vatRate());
    BigDecimal vat = calculator.vat(subtotal, vatRate);
    BigDecimal amountInclVat = calculator.amountInclVat(subtotal, vat);
    List<DiscountTier> tiers = repository.findDiscountTiers(country.id());
    BigDecimal discountRate = calculator.discountRate(amountInclVat, tiers);
    BigDecimal discount = calculator.discount(amountInclVat, discountRate);
    BigDecimal total = calculator.total(amountInclVat, discount, deliveryFee);
    return new PriceBreakdown(
        country.code(),
        country.currencyCode(),
        scenario,
        lines,
        subtotal,
        vatRate,
        vat,
        amountInclVat,
        discountRate,
        discount,
        deliveryFee,
        total);
  }

  /** Returns the delivery fee: 0.00 for pickup, the fee of the active zone for delivery. */
  private BigDecimal checkDelivery(
      Scenario scenario, Long zoneId, String countryCode, Problems problems) {
    if (scenario == Scenario.PICKUP) {
      if (zoneId != null) {
        problems.add(VALIDATION_ERROR, "deliveryZoneId", "must be empty for pickup");
      }
      return calculator.round(BigDecimal.ZERO);
    }
    if (zoneId == null) {
      problems.add(VALIDATION_ERROR, "deliveryZoneId", "is required for delivery");
      return calculator.round(BigDecimal.ZERO);
    }
    return zoneFees
        .activeFee(countryCode, zoneId)
        .map(calculator::round)
        .orElseGet(
            () -> {
              problems.add(
                  UNKNOWN_DELIVERY_ZONE,
                  "deliveryZoneId",
                  "Delivery zone " + zoneId + " does not exist in " + countryCode);
              return calculator.round(BigDecimal.ZERO);
            });
  }

  /** Everything the checks need, read from the database with one query per kind of data. */
  private record CartData(
      CountryPricing country,
      Map<Long, ProductPrice> products,
      Map<Long, ModifierPrice> modifiers,
      Set<Long> productIdsWithSizes) {}

  private CartData loadData(CountryPricing country, List<PricingItem> items) {
    Set<Long> productIds = new LinkedHashSet<>();
    Set<Long> modifierIds = new LinkedHashSet<>();
    for (PricingItem item : items) {
      if (item.productId() != null) {
        productIds.add(item.productId());
      }
      modifierIds.addAll(modifierIdsOf(item).stream().filter(Objects::nonNull).toList());
    }
    Map<Long, ProductPrice> products =
        repository.findProducts(country.id(), productIds).stream()
            .collect(Collectors.toMap(ProductPrice::id, Function.identity()));
    Map<Long, ModifierPrice> modifiers =
        repository.findModifiers(country.id(), modifierIds).stream()
            .collect(Collectors.toMap(ModifierPrice::id, Function.identity()));
    Set<Long> withSizes =
        new HashSet<>(repository.findProductIdsWithSizes(country.id(), productIds));
    return new CartData(country, products, modifiers, withSizes);
  }

  private CountryPricing findCountry(String rawCode) {
    String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    CountryPricing country =
        repository
            .findCountry(code)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST,
                        UNKNOWN_COUNTRY,
                        "Unknown country code",
                        Map.of("country", "Unknown country code")));
    if (country.vatRate() == null) {
      // Not the client's fault: the data of this country is incomplete.
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          PRICING_CONFIG_MISSING,
          "VAT rate is not set for " + country.code());
    }
    return country;
  }

  /** Returns the priced line, or null if this line has problems (they are added to problems). */
  private PricedLine priceItem(int index, PricingItem item, CartData data, Problems problems) {
    int problemsBefore = problems.size();
    String prefix = "items[" + index + "]";

    if (item.quantity() < 1) {
      problems.add(VALIDATION_ERROR, prefix + ".quantity", "must be 1 or more");
    } else if (item.quantity() > PricingLimits.MAX_QUANTITY) {
      problems.add(
          VALIDATION_ERROR,
          prefix + ".quantity",
          "must be " + PricingLimits.MAX_QUANTITY + " or less");
    }

    ProductPrice product = item.productId() == null ? null : data.products().get(item.productId());
    if (product == null || !product.visible()) {
      problems.add(
          UNKNOWN_PRODUCT,
          prefix + ".productId",
          "Product " + item.productId() + " does not exist");
    } else if (product.price() == null) {
      problems.add(
          PRODUCT_NOT_SOLD,
          prefix + ".productId",
          product.name() + " is not sold in " + data.country().code());
    } else if (!product.inStock()) {
      problems.add(OUT_OF_STOCK, prefix + ".productId", product.name() + " is out of stock");
    }

    List<PricedModifier> chosen = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    List<Long> modifierIds = modifierIdsOf(item);
    for (int j = 0; j < modifierIds.size(); j++) {
      PricedModifier priced =
          checkModifier(
              prefix + ".modifierIds[" + j + "]",
              modifierIds.get(j),
              item.productId(),
              data,
              seen,
              problems);
      if (priced != null) {
        chosen.add(priced);
      }
    }

    // The size rule is checked last, only for a line that is fine otherwise: a wrong modifier
    // would make the number of chosen sizes wrong too, and the client has to fix it first anyway.
    if (problems.size() == problemsBefore && data.productIdsWithSizes().contains(product.id())) {
      long sizes = chosen.stream().filter(m -> m.type() == ModifierType.SIZE).count();
      if (sizes != 1) {
        problems.add(INVALID_MODIFIER, prefix + ".modifierIds", "choose exactly one size");
      }
    }

    if (problems.size() > problemsBefore) {
      return null;
    }

    BigDecimal unitPrice =
        calculator.unitPrice(
            product.price(), chosen.stream().map(PricedModifier::priceDelta).toList());
    BigDecimal lineTotal = calculator.lineTotal(unitPrice, item.quantity());
    return new PricedLine(
        product.id(), product.name(), item.quantity(), unitPrice, lineTotal, chosen);
  }

  private PricedModifier checkModifier(
      String field,
      Long modifierId,
      Long productId,
      CartData data,
      Set<Long> seen,
      Problems problems) {
    ModifierPrice modifier = modifierId == null ? null : data.modifiers().get(modifierId);
    if (modifier == null || !modifier.visible()) {
      problems.add(INVALID_MODIFIER, field, "Modifier " + modifierId + " does not exist");
      return null;
    }
    if (productId == null || modifier.productId() != productId) {
      problems.add(
          INVALID_MODIFIER,
          field,
          "Modifier " + modifier.name() + " does not belong to product " + productId);
      return null;
    }
    if (modifier.priceDelta() == null) {
      problems.add(
          INVALID_MODIFIER,
          field,
          "Modifier " + modifier.name() + " is not available in " + data.country().code());
      return null;
    }
    if (!seen.add(modifierId)) {
      problems.add(INVALID_MODIFIER, field, "Modifier " + modifier.name() + " is chosen twice");
      return null;
    }
    return new PricedModifier(
        modifier.id(), modifier.name(), modifier.type(), modifier.priceDelta());
  }

  private static List<Long> modifierIdsOf(PricingItem item) {
    return item.modifierIds() == null ? List.of() : item.modifierIds();
  }

  private static ApiException invalidCart(String itemsMessage) {
    return new ApiException(
        HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Invalid cart", Map.of("items", itemsMessage));
  }

  /** Collects all problems of a cart. The first problem decides the error code. */
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
        throw new ApiException(
            HttpStatus.BAD_REQUEST, code, "Some cart items cannot be ordered", fields);
      }
    }
  }
}
