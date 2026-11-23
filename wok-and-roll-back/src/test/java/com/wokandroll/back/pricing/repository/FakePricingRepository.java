package com.wokandroll.back.pricing.repository;

import com.wokandroll.back.pricing.dto.DiscountTier;
import com.wokandroll.back.pricing.dto.ModifierType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory data for tests, so the tests need no database. Same idea as the seed menu. */
public class FakePricingRepository implements PricingRepository {

  private final Map<String, CountryPricing> countries = new HashMap<>();
  private final Map<Long, ProductPrice> products = new HashMap<>();
  private final Map<Long, ModifierPrice> modifiers = new HashMap<>();

  /**
   * Country UZB (VAT 15.00), country GEO without a VAT rate, and a small menu:
   *
   * <ul>
   *   <li>5 Firecracker udon 48.00, 8 Gyoza 32.00 (no sizes)
   *   <li>6 Miso mushroom rice 44.00 with sizes: 110 Regular +0.00, 111 Large +6.00, and the extra
   *       112 Extra egg +3.00
   *   <li>7 Teriyaki chicken noodles 52.00 and 9 Edamame 24.00: their sizes cannot be ordered in
   *       UZB (no price, hidden), so these dishes need no size
   *   <li>2 Tuna nigiri (out of stock), 11 hidden dish, 12 dish without a price in this country
   *   <li>extras: 100 Extra chili +5.50 (udon), 101 Extra sauce +4.00 (gyoza), 102 hidden, 103 no
   *       price
   * </ul>
   */
  public static FakePricingRepository sampleMenu() {
    FakePricingRepository repo = new FakePricingRepository();
    repo.countries.put("UZB", new CountryPricing(1, "UZB", "UZS", new BigDecimal("15.00")));
    repo.countries.put("GEO", new CountryPricing(3, "GEO", "GEL", null));
    repo.products.put(5L, new ProductPrice(5, "Firecracker udon", true, price("48.00"), true));
    repo.products.put(8L, new ProductPrice(8, "Gyoza", true, price("32.00"), true));
    repo.products.put(6L, new ProductPrice(6, "Miso mushroom rice", true, price("44.00"), true));
    repo.products.put(
        7L, new ProductPrice(7, "Teriyaki chicken noodles", true, price("52.00"), true));
    repo.products.put(9L, new ProductPrice(9, "Edamame", true, price("24.00"), true));
    repo.products.put(2L, new ProductPrice(2, "Tuna nigiri", true, price("48.00"), false));
    repo.products.put(11L, new ProductPrice(11, "Hidden dish", false, price("10.00"), true));
    repo.products.put(12L, new ProductPrice(12, "Not sold here", true, null, true));

    repo.addModifier(100, 5, ModifierType.EXTRA, "Extra chili", true, price("5.50"));
    repo.addModifier(101, 8, ModifierType.EXTRA, "Extra sauce", true, price("4.00"));
    repo.addModifier(102, 5, ModifierType.EXTRA, "Hidden extra", false, price("1.00"));
    repo.addModifier(103, 5, ModifierType.EXTRA, "No price extra", true, null);
    repo.addModifier(110, 6, ModifierType.SIZE, "Regular", true, price("0.00"));
    repo.addModifier(111, 6, ModifierType.SIZE, "Large", true, price("6.00"));
    repo.addModifier(112, 6, ModifierType.EXTRA, "Extra egg", true, price("3.00"));
    repo.addModifier(120, 7, ModifierType.SIZE, "Large", true, null);
    repo.addModifier(130, 9, ModifierType.SIZE, "Large", false, price("2.00"));
    return repo;
  }

  /** A repository with one country and one product, for tests that feed prices from a file. */
  public static FakePricingRepository oneProduct(
      String countryCode, String currency, String vatRate, String productPrice) {
    FakePricingRepository repo = new FakePricingRepository();
    repo.countries.put(
        countryCode, new CountryPricing(1, countryCode, currency, new BigDecimal(vatRate)));
    repo.products.put(1L, new ProductPrice(1, "Dish", true, price(productPrice), true));
    return repo;
  }

  private void addModifier(
      long id,
      long productId,
      ModifierType type,
      String name,
      boolean visible,
      BigDecimal priceDelta) {
    modifiers.put(id, new ModifierPrice(id, productId, type, name, visible, priceDelta));
  }

  private static BigDecimal price(String value) {
    return new BigDecimal(value);
  }

  /** 100 / 500 / 700 / 1000 / 5000 with 3 / 5 / 7 / 10 / 15 %, the same for every country. */
  @Override
  public List<DiscountTier> findDiscountTiers(long countryId) {
    return List.of(
        new DiscountTier(price("100"), price("3")),
        new DiscountTier(price("500"), price("5")),
        new DiscountTier(price("700"), price("7")),
        new DiscountTier(price("1000"), price("10")),
        new DiscountTier(price("5000"), price("15")));
  }

  @Override
  public Optional<CountryPricing> findCountry(String code) {
    return Optional.ofNullable(countries.get(code));
  }

  @Override
  public List<ProductPrice> findProducts(long countryId, Collection<Long> productIds) {
    return productIds.stream().map(products::get).filter(Objects::nonNull).toList();
  }

  @Override
  public List<ModifierPrice> findModifiers(long countryId, Collection<Long> modifierIds) {
    return modifierIds.stream().map(modifiers::get).filter(Objects::nonNull).toList();
  }

  @Override
  public List<Long> findProductIdsWithSizes(long countryId, Collection<Long> productIds) {
    return modifiers.values().stream()
        .filter(m -> m.type() == ModifierType.SIZE && m.visible() && m.priceDelta() != null)
        .map(ModifierPrice::productId)
        .filter(productIds::contains)
        .distinct()
        .toList();
  }
}
